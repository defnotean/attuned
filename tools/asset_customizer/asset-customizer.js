const canvas = document.querySelector("#previewCanvas");
const ctx = canvas.getContext("2d");

const controls = {
	assetSelect: document.querySelector("#assetSelect"),
	viewMode: document.querySelector("#viewMode"),
	displayPreset: document.querySelector("#displayPreset"),
	rotX: document.querySelector("#rotX"),
	rotY: document.querySelector("#rotY"),
	rotZ: document.querySelector("#rotZ"),
	zoom: document.querySelector("#zoom"),
	offsetX: document.querySelector("#offsetX"),
	offsetY: document.querySelector("#offsetY"),
	frame: document.querySelector("#frame"),
	playToggle: document.querySelector("#playToggle"),
	resetView: document.querySelector("#resetView"),
	refreshAsset: document.querySelector("#refreshAsset"),
	copyTransform: document.querySelector("#copyTransform"),
	exportPng: document.querySelector("#exportPng"),
	transformOutput: document.querySelector("#transformOutput")
};

const labels = {
	rotX: document.querySelector("#rotXValue"),
	rotY: document.querySelector("#rotYValue"),
	rotZ: document.querySelector("#rotZValue"),
	zoom: document.querySelector("#zoomValue"),
	offsetX: document.querySelector("#offsetXValue"),
	offsetY: document.querySelector("#offsetYValue"),
	frame: document.querySelector("#frameValue")
};

const statusLabel = document.querySelector("#assetStatus");
const assetName = document.querySelector("#assetName");
const assetKind = document.querySelector("#assetKind");
const assetPaths = document.querySelector("#assetPaths");

let manifest = [];
let selectedAsset = null;
let selectedModel = null;
let selectedTexture = null;
let textureData = null;
let frameCount = 1;
let playing = false;
let playTimer = 0;

init().catch((error) => {
	statusLabel.textContent = error.message;
	statusLabel.style.color = "var(--danger)";
});

async function init() {
	manifest = await fetchJson("asset-manifest.json");
	for (const asset of manifest) {
		const option = document.createElement("option");
		option.value = asset.id;
		option.textContent = asset.name;
		controls.assetSelect.append(option);
	}

	bindControls();
	await selectAsset(manifest[0].id);
}

function bindControls() {
	controls.assetSelect.addEventListener("change", () => selectAsset(controls.assetSelect.value));
	controls.viewMode.addEventListener("change", applyPresetForView);
	controls.displayPreset.addEventListener("change", () => applyPreset(controls.displayPreset.value));
	controls.resetView.addEventListener("click", () => applyPreset(controls.displayPreset.value));
	controls.refreshAsset.addEventListener("click", () => selectAsset(controls.assetSelect.value));
	controls.copyTransform.addEventListener("click", copyTransform);
	controls.exportPng.addEventListener("click", exportPng);
	controls.playToggle.addEventListener("click", togglePlay);
	for (const key of ["rotX", "rotY", "rotZ", "zoom", "offsetX", "offsetY", "frame"]) {
		controls[key].addEventListener("input", () => {
			updateLabels();
			updateTransformOutput();
			render();
		});
	}
}

async function selectAsset(assetId) {
	selectedAsset = manifest.find((asset) => asset.id === assetId);
	if (!selectedAsset) {
		throw new Error(`Unknown asset: ${assetId}`);
	}

	statusLabel.textContent = "Loading model and texture...";
	selectedModel = await fetchJson(cacheBust(selectedAsset.model));
	selectedTexture = await loadImage(cacheBust(selectedAsset.texture));
	textureData = imageDataFor(selectedTexture);
	frameCount = selectedTexture.height % selectedTexture.width === 0
		? Math.max(1, selectedTexture.height / selectedTexture.width)
		: 1;

	controls.frame.max = Math.max(0, frameCount - 1).toString();
	controls.frame.value = "0";
	populatePresets();
	applyPresetForView();

	assetName.textContent = selectedAsset.name;
	assetKind.textContent = selectedModel.parent || selectedAsset.kind;
	assetPaths.textContent = `${selectedAsset.texture} | ${selectedAsset.model}`;
	statusLabel.textContent = `${selectedTexture.width}x${selectedTexture.height}, ${frameCount} frame${frameCount === 1 ? "" : "s"}`;
	render();
}

function populatePresets() {
	controls.displayPreset.replaceChildren();
	const display = selectedModel.display || {};
	const keys = Object.keys(display);
	const presets = keys.length ? keys : ["gui"];
	for (const key of presets) {
		const option = document.createElement("option");
		option.value = key;
		option.textContent = key;
		controls.displayPreset.append(option);
	}
	controls.displayPreset.value = presets.includes("gui") ? "gui" : presets[0];
}

function applyPreset(name) {
	const preset = selectedModel.display?.[name] || {};
	const rotation = preset.rotation || [0, 0, 0];
	const translation = preset.translation || [0, 0, 0];
	const scale = preset.scale || [1, 1, 1];
	controls.rotX.value = Math.round(rotation[0] || 0);
	controls.rotY.value = Math.round(rotation[1] || 0);
	controls.rotZ.value = Math.round(rotation[2] || 0);
	controls.offsetX.value = Math.round((translation[0] || 0) * 8);
	controls.offsetY.value = Math.round(-(translation[1] || 0) * 8);
	controls.zoom.value = Math.round(26 * averageScale(scale));
	updateLabels();
	updateTransformOutput();
	render();
}

function applyPresetForView() {
	const mode = controls.viewMode.value;
	if (mode === "firstperson" && selectedModel.display?.firstperson_righthand) {
		controls.displayPreset.value = "firstperson_righthand";
	} else if (mode === "thirdperson" && selectedModel.display?.thirdperson_righthand) {
		controls.displayPreset.value = "thirdperson_righthand";
	} else if ((mode === "model" || mode === "slot") && selectedModel.display?.gui) {
		controls.displayPreset.value = "gui";
	}
	applyPreset(controls.displayPreset.value);
}

function render() {
	clear();
	drawBackdrop();
	drawGrid();
	const mode = controls.viewMode.value;
	if (mode === "texture") {
		drawTextureAtlas();
	} else if (mode === "slot") {
		drawInventorySlot();
	} else if (mode === "firstperson") {
		drawHeldItemScene("firstperson");
	} else if (mode === "thirdperson") {
		drawHeldItemScene("thirdperson");
	} else if (hasBlockElements()) {
		drawBlockModel({
			originX: canvas.width / 2 + numberValue("offsetX"),
			originY: canvas.height / 2 + numberValue("offsetY"),
			zoom: numberValue("zoom") * 0.45,
			depthSkew: 0.28
		});
	} else {
		drawReadableGeneratedItem({
			originX: canvas.width / 2 + numberValue("offsetX"),
			originY: canvas.height / 2 + numberValue("offsetY"),
			size: numberValue("zoom") * 6.8,
			shadow: 5
		});
	}
}

function clear() {
	ctx.clearRect(0, 0, canvas.width, canvas.height);
	ctx.imageSmoothingEnabled = false;
}

function drawBackdrop() {
	const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height);
	gradient.addColorStop(0, "#111923");
	gradient.addColorStop(1, "#070a0f");
	ctx.fillStyle = gradient;
	ctx.fillRect(0, 0, canvas.width, canvas.height);
}

function drawGrid() {
	ctx.save();
	ctx.strokeStyle = "rgba(69, 85, 105, 0.22)";
	ctx.lineWidth = 1;
	for (let x = 0; x <= canvas.width; x += 32) {
		ctx.beginPath();
		ctx.moveTo(x + 0.5, 0);
		ctx.lineTo(x + 0.5, canvas.height);
		ctx.stroke();
	}
	for (let y = 0; y <= canvas.height; y += 32) {
		ctx.beginPath();
		ctx.moveTo(0, y + 0.5);
		ctx.lineTo(canvas.width, y + 0.5);
		ctx.stroke();
	}
	ctx.restore();
}

function drawInventorySlot() {
	const scale = 10;
	const slotSize = 18 * scale;
	const x = canvas.width / 2 - slotSize / 2 + numberValue("offsetX");
	const y = canvas.height / 2 - slotSize / 2 + numberValue("offsetY");
	ctx.fillStyle = "#080a0e";
	ctx.fillRect(x - 8, y - 8, slotSize + 16, slotSize + 16);
	ctx.fillStyle = "#2b313d";
	ctx.fillRect(x, y, slotSize, slotSize);
	ctx.fillStyle = "#5b6575";
	ctx.fillRect(x + scale, y + scale, slotSize - scale, slotSize - scale);
	ctx.fillStyle = "#161b24";
	ctx.fillRect(x + scale, y + scale, slotSize - scale * 2, slotSize - scale * 2);
	ctx.fillStyle = "#05070b";
	ctx.fillRect(x + scale * 2, y + scale * 2, slotSize - scale * 4, slotSize - scale * 4);
	if (hasBlockElements()) {
		drawBlockModel({
			originX: x + slotSize / 2,
			originY: y + slotSize / 2,
			zoom: scale * 0.82,
			depthSkew: 0.22
		});
	} else {
		drawSpriteImage(x + scale, y + scale, 16 * scale, 16 * scale, true);
	}
}

function drawTextureAtlas() {
	const margin = 56;
	const fit = Math.min(
		(canvas.width - margin * 2) / selectedTexture.width,
		(canvas.height - margin * 2) / selectedTexture.height
	);
	const scale = Math.max(1, Math.floor(fit));
	const width = selectedTexture.width * scale;
	const height = selectedTexture.height * scale;
	const x = canvas.width / 2 - width / 2;
	const y = canvas.height / 2 - height / 2;
	drawChecker(x, y, width, height, 16);
	ctx.drawImage(selectedTexture, x, y, width, height);
	ctx.strokeStyle = "#32d0d4";
	ctx.lineWidth = 2;
	ctx.strokeRect(x, y, width, height);
	if (frameCount > 1) {
		ctx.strokeStyle = "rgba(210, 134, 79, 0.9)";
		for (let frame = 1; frame < frameCount; frame++) {
			const lineY = y + frame * selectedTexture.width * scale;
			ctx.beginPath();
			ctx.moveTo(x, lineY);
			ctx.lineTo(x + width, lineY);
			ctx.stroke();
		}
	}
}

function drawHeldItemScene(kind) {
	if (kind === "firstperson") {
		drawFirstPersonContext();
		if (hasBlockElements()) {
			drawBlockModel({
				originX: canvas.width * 0.67 + numberValue("offsetX") * 1.1,
				originY: canvas.height * 0.61 + numberValue("offsetY") * 1.1,
				zoom: numberValue("zoom") * 0.43,
				depthSkew: 0.32
			});
		} else {
			drawReadableGeneratedItem({
				originX: canvas.width * 0.67 + numberValue("offsetX") * 1.1,
				originY: canvas.height * 0.61 + numberValue("offsetY") * 1.1,
				size: numberValue("zoom") * 7.0,
				shadow: 6
			});
		}
		drawFirstPersonHand();
		return;
	}

	drawThirdPersonContext();
	if (hasBlockElements()) {
		drawBlockModel({
			originX: canvas.width * 0.59 + numberValue("offsetX"),
			originY: canvas.height * 0.46 + numberValue("offsetY"),
			zoom: numberValue("zoom") * 0.28,
			depthSkew: 0.3
		});
	} else {
		drawReadableGeneratedItem({
			originX: canvas.width * 0.59 + numberValue("offsetX"),
			originY: canvas.height * 0.46 + numberValue("offsetY"),
			size: numberValue("zoom") * 4.6,
			shadow: 4
		});
	}
	drawThirdPersonArm();
}

function drawReadableGeneratedItem(options) {
	const size = options.size;
	const rotation = radians(numberValue("rotZ"));
	ctx.save();
	ctx.translate(options.originX, options.originY);
	ctx.rotate(rotation);
	for (let step = options.shadow; step >= 1; step--) {
		ctx.globalAlpha = 0.16;
		drawSpriteImage(-size / 2 - step * 1.2, -size / 2 + step * 0.9, size, size, false);
	}
	ctx.globalAlpha = 1;
	ctx.filter = "drop-shadow(0 0 10px rgba(50, 208, 212, 0.45))";
	drawSpriteImage(-size / 2, -size / 2, size, size, false);
	ctx.filter = "none";
	ctx.restore();
}

function drawGeneratedItemModel(options) {
	const frameInfo = activeFrameInfo();
	const faces = generatedItemFaces(frameInfo);
	const transform = activeModelTransform(options);
	for (const face of faces) {
		for (const point of face.points) {
			point.world = transformPoint(point, transform);
			point.screen = projectModelPoint(point.world, transform);
		}
		face.depth = face.points.reduce((sum, point) => sum + point.world.z, 0) / face.points.length;
	}
	faces.sort((a, b) => a.depth - b.depth);
	for (const face of faces) {
		fillProjectedFace(face);
	}
}

function generatedItemFaces(frameInfo) {
	const faces = [];
	const unit = 16 / frameInfo.size;
	for (let y = 0; y < frameInfo.size; y++) {
		for (let x = 0; x < frameInfo.size; x++) {
			const color = framePixel(frameInfo, x, y);
			if (color.a <= 24) {
				continue;
			}
			const x0 = x * unit - 8;
			const x1 = (x + 1) * unit - 8;
			const y0 = 8 - (y + 1) * unit;
			const y1 = 8 - y * unit;
			const front = 0.5;
			const back = -0.5;
			faces.push(face([
				{x: x0, y: y0, z: front},
				{x: x1, y: y0, z: front},
				{x: x1, y: y1, z: front},
				{x: x0, y: y1, z: front}
			], rgba(color, 1), "front"));
			if (isFramePixelEmpty(frameInfo, x - 1, y)) {
				faces.push(sideFace(x0, y0, y1, front, back, color, "left"));
			}
			if (isFramePixelEmpty(frameInfo, x + 1, y)) {
				faces.push(sideFace(x1, y0, y1, front, back, color, "right"));
			}
			if (isFramePixelEmpty(frameInfo, x, y - 1)) {
				faces.push(edgeFace(x0, x1, y1, front, back, color, "top"));
			}
			if (isFramePixelEmpty(frameInfo, x, y + 1)) {
				faces.push(edgeFace(x0, x1, y0, front, back, color, "bottom"));
			}
		}
	}
	return faces;
}

function sideFace(x, y0, y1, front, back, color, name) {
	return face([
		{x, y: y0, z: back},
		{x, y: y0, z: front},
		{x, y: y1, z: front},
		{x, y: y1, z: back}
	], rgba(color, name === "left" ? 0.64 : 0.74), name);
}

function edgeFace(x0, x1, y, front, back, color, name) {
	return face([
		{x: x0, y, z: back},
		{x: x1, y, z: back},
		{x: x1, y, z: front},
		{x: x0, y, z: front}
	], rgba(color, name === "top" ? 0.84 : 0.55), name);
}

function face(points, fill, name) {
	return {points, fill, name, depth: 0};
}

function activeFrameInfo() {
	const size = selectedTexture.width;
	const height = frameCount > 1 ? size : selectedTexture.height;
	return {
		size,
		height,
		frame: frameCount > 1 ? numberValue("frame") : 0,
		offsetY: frameCount > 1 ? numberValue("frame") * size : 0
	};
}

function framePixel(frameInfo, x, y) {
	const sourceY = frameInfo.offsetY + y;
	const index = (sourceY * textureData.width + x) * 4;
	return {
		r: textureData.data[index],
		g: textureData.data[index + 1],
		b: textureData.data[index + 2],
		a: textureData.data[index + 3]
	};
}

function isFramePixelEmpty(frameInfo, x, y) {
	if (x < 0 || y < 0 || x >= frameInfo.size || y >= frameInfo.height) {
		return true;
	}
	return framePixel(frameInfo, x, y).a <= 24;
}

function activeModelTransform(options) {
	return {
		rotationX: radians(numberValue("rotX")),
		rotationY: radians(numberValue("rotY")),
		rotationZ: radians(numberValue("rotZ")),
		originX: options.originX,
		originY: options.originY,
		zoom: options.zoom,
		depthSkew: options.depthSkew
	};
}

function transformPoint(point, transform) {
	let {x, y, z} = point;
	[y, z] = rotatePair(y, z, transform.rotationX);
	[x, z] = rotatePair(x, z, transform.rotationY);
	[x, y] = rotatePair(x, y, transform.rotationZ);
	return {x, y, z};
}

function projectModelPoint(point, transform) {
	return {
		x: transform.originX + point.x * transform.zoom + point.z * transform.zoom * transform.depthSkew,
		y: transform.originY - point.y * transform.zoom + point.z * transform.zoom * transform.depthSkew * 0.42
	};
}

function fillProjectedFace(face) {
	const points = face.points.map((point) => point.screen);
	ctx.beginPath();
	ctx.moveTo(points[0].x, points[0].y);
	for (let index = 1; index < points.length; index++) {
		ctx.lineTo(points[index].x, points[index].y);
	}
	ctx.closePath();
	ctx.fillStyle = face.fill;
	ctx.fill();
	if (face.name !== "front") {
		ctx.strokeStyle = "rgba(6, 12, 18, 0.18)";
		ctx.lineWidth = 1;
		ctx.stroke();
	}
}

function rgba(color, multiplier) {
	return `rgba(${Math.round(color.r * multiplier)}, ${Math.round(color.g * multiplier)}, ${Math.round(color.b * multiplier)}, ${color.a / 255})`;
}

function drawFirstPersonContext() {
	ctx.save();
	const horizon = canvas.height * 0.44;
	ctx.fillStyle = "#16202b";
	ctx.fillRect(0, 0, canvas.width, horizon);
	ctx.fillStyle = "#222b22";
	ctx.fillRect(0, horizon, canvas.width, canvas.height - horizon);
	ctx.fillStyle = "rgba(255,255,255,0.42)";
	ctx.fillRect(canvas.width / 2 - 8, canvas.height / 2 - 1, 16, 2);
	ctx.fillRect(canvas.width / 2 - 1, canvas.height / 2 - 8, 2, 16);
	ctx.fillStyle = "rgba(8, 10, 14, 0.66)";
	ctx.fillRect(canvas.width / 2 - 176, canvas.height - 54, 352, 44);
	for (let slot = 0; slot < 9; slot++) {
		const x = canvas.width / 2 - 162 + slot * 36;
		ctx.fillStyle = slot === 4 ? "#d7efe8" : "#333943";
		ctx.fillRect(x, canvas.height - 48, 32, 32);
		ctx.fillStyle = "#0d1118";
		ctx.fillRect(x + 3, canvas.height - 45, 26, 26);
	}
	ctx.restore();
}

function drawFirstPersonHand() {
	ctx.save();
	ctx.translate(canvas.width * 0.74, canvas.height * 0.75);
	ctx.rotate(radians(-20));
	ctx.fillStyle = "#3b2a25";
	ctx.fillRect(-44, -8, 110, 38);
	ctx.fillStyle = "#6d4b3f";
	ctx.fillRect(40, -10, 54, 42);
	ctx.fillStyle = "#211c25";
	ctx.fillRect(-48, 20, 82, 34);
	ctx.restore();
}

function drawThirdPersonContext() {
	ctx.save();
	ctx.fillStyle = "#111922";
	ctx.fillRect(0, 0, canvas.width, canvas.height);
	ctx.fillStyle = "#1c271f";
	ctx.fillRect(0, canvas.height * 0.64, canvas.width, canvas.height * 0.36);
	ctx.translate(canvas.width * 0.42, canvas.height * 0.36);
	ctx.fillStyle = "#202733";
	ctx.fillRect(-34, 16, 68, 96);
	ctx.fillStyle = "#3a2b25";
	ctx.fillRect(-28, -34, 56, 50);
	ctx.fillStyle = "#121922";
	ctx.fillRect(-26, -56, 52, 22);
	ctx.fillStyle = "#2b3342";
	ctx.fillRect(-46, 24, 22, 86);
	ctx.restore();
}

function drawThirdPersonArm() {
	ctx.save();
	ctx.translate(canvas.width * 0.47, canvas.height * 0.47);
	ctx.rotate(radians(-18));
	ctx.fillStyle = "#2b3342";
	ctx.fillRect(0, -12, 86, 24);
	ctx.fillStyle = "#6d4b3f";
	ctx.fillRect(72, -14, 26, 28);
	ctx.restore();
}

function drawBlockModel(options = {}) {
	const faces = [];
	const view = {
		originX: options.originX ?? canvas.width / 2 + numberValue("offsetX"),
		originY: options.originY ?? canvas.height / 2 + numberValue("offsetY"),
		zoom: options.zoom ?? numberValue("zoom") * 0.45,
		depthSkew: options.depthSkew ?? 0.25
	};
	for (const element of selectedModel.elements) {
		const corners = cuboidCorners(element.from, element.to).map((point) => rotatePoint(point));
		addFace(faces, element, "north", [0, 1, 2, 3], corners);
		addFace(faces, element, "south", [4, 5, 6, 7], corners);
		addFace(faces, element, "west", [0, 3, 7, 4], corners);
		addFace(faces, element, "east", [1, 2, 6, 5], corners);
		addFace(faces, element, "up", [3, 2, 6, 7], corners);
		addFace(faces, element, "down", [0, 1, 5, 4], corners);
	}
	faces.sort((a, b) => a.depth - b.depth);
	for (const face of faces) {
		const points = face.points.map((point) => projectPoint(point, view));
		ctx.beginPath();
		ctx.moveTo(points[0].x, points[0].y);
		for (let index = 1; index < points.length; index++) {
			ctx.lineTo(points[index].x, points[index].y);
		}
		ctx.closePath();
		ctx.fillStyle = face.color;
		ctx.fill();
		ctx.strokeStyle = "rgba(255,255,255,0.10)";
		ctx.stroke();
	}
}

function addFace(faces, element, faceName, indexes, corners) {
	const face = element.faces?.[faceName];
	if (!face) {
		return;
	}
	const points = indexes.map((index) => corners[index]);
	const depth = points.reduce((sum, point) => sum + point.z, 0) / points.length;
	faces.push({
		points,
		depth,
		color: sampleFaceColor(face.uv || [0, 0, 16, 16], faceName)
	});
}

function cuboidCorners(from, to) {
	const [x0, y0, z0] = from;
	const [x1, y1, z1] = to;
	return [
		{x: x0 - 8, y: y0 - 8, z: z0 - 8},
		{x: x1 - 8, y: y0 - 8, z: z0 - 8},
		{x: x1 - 8, y: y1 - 8, z: z0 - 8},
		{x: x0 - 8, y: y1 - 8, z: z0 - 8},
		{x: x0 - 8, y: y0 - 8, z: z1 - 8},
		{x: x1 - 8, y: y0 - 8, z: z1 - 8},
		{x: x1 - 8, y: y1 - 8, z: z1 - 8},
		{x: x0 - 8, y: y1 - 8, z: z1 - 8}
	];
}

function rotatePoint(point) {
	let {x, y, z} = point;
	[y, z] = rotatePair(y, z, radians(numberValue("rotX")));
	[x, z] = rotatePair(x, z, radians(numberValue("rotY")));
	[x, y] = rotatePair(x, y, radians(numberValue("rotZ")));
	return {x, y, z};
}

function rotatePair(a, b, angle) {
	const cos = Math.cos(angle);
	const sin = Math.sin(angle);
	return [a * cos - b * sin, a * sin + b * cos];
}

function projectPoint(point, view) {
	return {
		x: view.originX + point.x * view.zoom + point.z * view.zoom * view.depthSkew,
		y: view.originY - point.y * view.zoom - point.z * view.zoom * view.depthSkew * 0.42
	};
}

function sampleFaceColor(uv, faceName) {
	const x = clamp(Math.floor((((uv[0] + uv[2]) * 0.5) / 16) * selectedTexture.width), 0, selectedTexture.width - 1);
	const y = clamp(Math.floor((((uv[1] + uv[3]) * 0.5) / 16) * selectedTexture.height), 0, selectedTexture.height - 1);
	const index = (y * textureData.width + x) * 4;
	const multiplier = faceShade(faceName);
	return `rgba(${shadeChannel(textureData.data[index], multiplier)}, ${shadeChannel(textureData.data[index + 1], multiplier)}, ${shadeChannel(textureData.data[index + 2], multiplier)}, 0.96)`;
}

function shadeChannel(value, multiplier) {
	return clamp(Math.round(value * multiplier), 0, 255);
}

function faceShade(faceName) {
	switch (faceName) {
		case "up":
			return 1.08;
		case "down":
			return 0.58;
		case "west":
			return 0.72;
		case "east":
			return 0.84;
		case "north":
			return 0.92;
		default:
			return 1.0;
	}
}

function drawSpriteImage(x, y, width, height, forceFrame) {
	const sourceWidth = selectedTexture.width;
	const sourceHeight = frameCount > 1 ? selectedTexture.width : selectedTexture.height;
	const frame = forceFrame || frameCount > 1 ? numberValue("frame") : 0;
	const sy = frameCount > 1 ? frame * selectedTexture.width : 0;
	ctx.drawImage(selectedTexture, 0, sy, sourceWidth, sourceHeight, x, y, width, height);
}

function drawChecker(x, y, width, height, size) {
	ctx.save();
	ctx.beginPath();
	ctx.rect(x, y, width, height);
	ctx.clip();
	for (let yy = y; yy < y + height; yy += size) {
		for (let xx = x; xx < x + width; xx += size) {
			const odd = (Math.floor((xx - x) / size) + Math.floor((yy - y) / size)) % 2;
			ctx.fillStyle = odd ? "#202834" : "#101720";
			ctx.fillRect(xx, yy, size, size);
		}
	}
	ctx.restore();
}

function imageDataFor(image) {
	const scratch = document.createElement("canvas");
	scratch.width = image.width;
	scratch.height = image.height;
	const scratchCtx = scratch.getContext("2d");
	scratchCtx.drawImage(image, 0, 0);
	return scratchCtx.getImageData(0, 0, image.width, image.height);
}

function updateLabels() {
	for (const key of Object.keys(labels)) {
		labels[key].textContent = controls[key].value;
	}
}

function updateTransformOutput() {
	const scale = Number((numberValue("zoom") / 26).toFixed(2));
	const transform = {
		rotation: [numberValue("rotX"), numberValue("rotY"), numberValue("rotZ")],
		translation: [
			Number((numberValue("offsetX") / 8).toFixed(2)),
			Number((-numberValue("offsetY") / 8).toFixed(2)),
			0
		],
		scale: [scale, scale, scale]
	};
	controls.transformOutput.value = JSON.stringify(transform, null, "\t");
}

async function copyTransform() {
	updateTransformOutput();
	try {
		await navigator.clipboard.writeText(controls.transformOutput.value);
		statusLabel.textContent = "Transform JSON copied.";
	} catch {
		controls.transformOutput.select();
		statusLabel.textContent = "Select/copy the transform JSON from the text box.";
	}
}

function exportPng() {
	const link = document.createElement("a");
	link.download = `${selectedAsset.id}-preview.png`;
	link.href = canvas.toDataURL("image/png");
	link.click();
}

function togglePlay() {
	playing = !playing;
	controls.playToggle.classList.toggle("active", playing);
	controls.playToggle.textContent = playing ? "Pause" : "Play";
	if (playing) {
		playTimer = window.setInterval(() => {
			controls.frame.value = ((numberValue("frame") + 1) % frameCount).toString();
			updateLabels();
			updateTransformOutput();
			render();
		}, 160);
	} else {
		window.clearInterval(playTimer);
	}
}

function hasBlockElements() {
	return Array.isArray(selectedModel.elements) && selectedModel.elements.length > 0;
}

function numberValue(key) {
	return Number(controls[key].value);
}

function averageScale(scale) {
	return (Number(scale[0] || 1) + Number(scale[1] || 1) + Number(scale[2] || 1)) / 3;
}

function radians(degrees) {
	return degrees * Math.PI / 180;
}

function clamp(value, min, max) {
	return Math.max(min, Math.min(max, value));
}

function cacheBust(path) {
	return `${path}?v=${Date.now()}`;
}

async function fetchJson(path) {
	const response = await fetch(path, {cache: "no-store"});
	if (!response.ok) {
		throw new Error(`Could not load ${path}: ${response.status}`);
	}
	return response.json();
}

function loadImage(path) {
	return new Promise((resolve, reject) => {
		const image = new Image();
		image.onload = () => resolve(image);
		image.onerror = () => reject(new Error(`Could not load image ${path}`));
		image.src = path;
	});
}
