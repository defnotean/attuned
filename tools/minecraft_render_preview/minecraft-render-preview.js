import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";

const MODEL_UNIT = 1 / 16;
const ASSETS = {
	model: "../../src/main/resources/assets/attuned/gltf/ocean_relic_trident.glb",
	texture: "../../src/main/resources/assets/attuned/textures/item/ocean_relic_trident_blockbench.png",
	baseModel: "../../src/main/resources/assets/attuned/models/item/ocean_relic_trident.json",
	throwingModel: "../../src/main/resources/assets/attuned/models/item/ocean_relic_trident_throwing.json"
};

const controls = {
	viewMode: document.querySelector("#viewMode"),
	displayPreset: document.querySelector("#displayPreset"),
	showPlayer: document.querySelector("#showPlayer"),
	showWire: document.querySelector("#showWire"),
	showGrid: document.querySelector("#showGrid"),
	autoRotate: document.querySelector("#autoRotate"),
	uvMode: document.querySelector("#uvMode"),
	textureFlip: document.querySelector("#textureFlip"),
	filterMode: document.querySelector("#filterMode"),
	wrapMode: document.querySelector("#wrapMode"),
	normalMode: document.querySelector("#normalMode"),
	rotX: document.querySelector("#rotX"),
	rotY: document.querySelector("#rotY"),
	rotZ: document.querySelector("#rotZ"),
	scale: document.querySelector("#scale"),
	posX: document.querySelector("#posX"),
	posY: document.querySelector("#posY"),
	posZ: document.querySelector("#posZ"),
	resetManual: document.querySelector("#resetManual"),
	reloadAssets: document.querySelector("#reloadAssets")
};

const labels = {};
for (const key of ["rotX", "rotY", "rotZ", "scale", "posX", "posY", "posZ"]) {
	labels[key] = document.querySelector(`#${key}Value`);
}

const statusLabel = document.querySelector("#status");
const statsNode = document.querySelector("#stats");
const overlayMode = document.querySelector("#overlayMode");
const assetPaths = document.querySelector("#assetPaths");
const canvas = document.querySelector("#renderCanvas");

const renderer = new THREE.WebGLRenderer({
	canvas,
	antialias: false,
	alpha: false,
	preserveDrawingBuffer: true,
	powerPreference: "high-performance"
});
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.setClearColor(0x6ea4f5, 1);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x6ea4f5);

const camera = new THREE.PerspectiveCamera(42, 1, 0.02, 120);
camera.position.set(4.6, 3.4, 6.6);

const orbit = new OrbitControls(camera, renderer.domElement);
orbit.enableDamping = true;
orbit.target.set(0, 1.15, 0);

const rootGroup = new THREE.Group();
const itemRoot = new THREE.Group();
const meshGroup = new THREE.Group();
const playerGroup = new THREE.Group();
const grid = new THREE.GridHelper(8, 16, 0x294050, 0x294050);
grid.position.y = 0;

scene.add(rootGroup);
rootGroup.add(playerGroup);
rootGroup.add(itemRoot);
itemRoot.add(meshGroup);
scene.add(grid);

const hemi = new THREE.HemisphereLight(0xb9dcff, 0x273019, 1.45);
const keyLight = new THREE.DirectionalLight(0xffffff, 1.35);
keyLight.position.set(-3, 6, 5);
const fillLight = new THREE.DirectionalLight(0x74f2ff, 0.45);
fillLight.position.set(5, 3, -4);
scene.add(hemi, keyLight, fillLight);

let blockbenchModel = null;
let baseItemModel = null;
let throwingItemModel = null;
let texture = null;
let mesh = null;
let wire = null;
let rawStats = null;
let lastTime = 0;

init().catch((error) => {
	console.error(error);
	statusLabel.textContent = error.message;
	statusLabel.style.color = "var(--danger)";
});

async function init() {
	buildPlayer();
	bindControls();
	await loadAssets();
	animate(0);
}

function bindControls() {
	for (const control of Object.values(controls)) {
		if (!control || control.tagName === "BUTTON") {
			continue;
		}
		control.addEventListener("input", handleControlChange);
		control.addEventListener("change", handleControlChange);
	}
	controls.resetManual.addEventListener("click", () => {
		for (const [key, value] of Object.entries({
			rotX: 0,
			rotY: 0,
			rotZ: 0,
			scale: 100,
			posX: 0,
			posY: 0,
			posZ: 0
		})) {
			controls[key].value = String(value);
		}
		updateScene();
	});
	controls.reloadAssets.addEventListener("click", () => {
		void loadAssets();
	});
	window.addEventListener("resize", resize);
}

async function loadAssets() {
	statusLabel.textContent = "Loading model, display transforms, and texture...";
	const stamp = Date.now();
	[blockbenchModel, baseItemModel, throwingItemModel] = await Promise.all([
		fetchModel(`${ASSETS.model}?v=${stamp}`),
		fetchJson(`${ASSETS.baseModel}?v=${stamp}`),
		fetchJson(`${ASSETS.throwingModel}?v=${stamp}`)
	]);
	texture = await loadTexture(`${ASSETS.texture}?v=${stamp}`);
	populateDisplayPresets();
	rebuildMesh();
	updateScene();
	assetPaths.textContent = `${ASSETS.model} | ${ASSETS.texture}`;
	statusLabel.textContent = "Loaded. Use controls to match the in-game render path.";
}

async function fetchJson(path) {
	const response = await fetch(path, { cache: "no-store" });
	if (!response.ok) {
		throw new Error(`Failed to load ${path}: ${response.status}`);
	}
	return response.json();
}

async function fetchModel(path) {
	const response = await fetch(path, { cache: "no-store" });
	if (!response.ok) {
		throw new Error(`Failed to load ${path}: ${response.status}`);
	}
	if (path.split("?")[0].endsWith(".glb")) {
		return parseGlbModel(await response.arrayBuffer());
	}
	return response.json();
}

function loadTexture(path) {
	return new Promise((resolve, reject) => {
		new THREE.TextureLoader().load(path, resolve, undefined, reject);
	});
}

function populateDisplayPresets() {
	const display = {
		...baseItemModel.display,
		...Object.fromEntries(Object.entries(throwingItemModel.display || {}).map(([key, value]) => [`throwing:${key}`, value]))
	};
	controls.displayPreset.replaceChildren();
	for (const key of Object.keys(display)) {
		const option = document.createElement("option");
		option.value = key;
		option.textContent = key;
		controls.displayPreset.append(option);
	}
	controls.displayPreset.value = "thirdperson_righthand";
}

function handleControlChange(event) {
	if (["uvMode", "textureFlip", "filterMode", "wrapMode", "normalMode"].includes(event.target.id)) {
		rebuildMesh();
	}
	if (event.target.id === "viewMode") {
		applyPresetForView();
	}
	updateScene();
}

function applyPresetForView() {
	const mode = controls.viewMode.value;
	if (mode === "firstperson" && baseItemModel.display?.firstperson_righthand) {
		controls.displayPreset.value = "firstperson_righthand";
	} else if (mode === "thirdperson" && baseItemModel.display?.thirdperson_righthand) {
		controls.displayPreset.value = "thirdperson_righthand";
	} else if (mode === "throwing" && throwingItemModel.display?.thirdperson_righthand) {
		controls.displayPreset.value = "throwing:thirdperson_righthand";
	} else if (mode === "raw" || mode === "blockbench") {
		controls.displayPreset.value = baseItemModel.display?.gui ? "gui" : controls.displayPreset.value;
	}
}

function rebuildMesh() {
	if (!blockbenchModel || !texture) {
		return;
	}
	disposeObject(mesh);
	disposeObject(wire);
	mesh = null;
	wire = null;
	meshGroup.clear();

	configureTexture(texture);
	const geometry = buildGeometry(blockbenchModel, {
		uvMode: controls.uvMode.value,
		normalMode: controls.normalMode.value
	});
	const material = new THREE.MeshLambertMaterial({
		map: texture,
		side: THREE.DoubleSide,
		transparent: true,
		alphaTest: 0.01
	});
	mesh = new THREE.Mesh(geometry, material);
	meshGroup.add(mesh);

	const wireGeometry = new THREE.WireframeGeometry(geometry);
	const wireMaterial = new THREE.LineBasicMaterial({
		color: 0x111820,
		transparent: true,
		opacity: 0.5,
		depthTest: false
	});
	wire = new THREE.LineSegments(wireGeometry, wireMaterial);
	meshGroup.add(wire);
	rawStats = collectStats(blockbenchModel, geometry);
	updateStats();
}

function configureTexture(target) {
	const filter = controls.filterMode.value;
	target.colorSpace = THREE.SRGBColorSpace;
	target.flipY = controls.textureFlip.value === "true";
	target.wrapS = controls.wrapMode.value === "repeat" ? THREE.RepeatWrapping : THREE.ClampToEdgeWrapping;
	target.wrapT = target.wrapS;
	target.magFilter = filter === "nearest" ? THREE.NearestFilter : THREE.LinearFilter;
	if (filter === "mipmap") {
		target.minFilter = THREE.LinearMipmapLinearFilter;
		target.generateMipmaps = true;
	} else {
		target.minFilter = filter === "nearest" ? THREE.NearestFilter : THREE.LinearFilter;
		target.generateMipmaps = false;
	}
	target.needsUpdate = true;
}

function buildGeometry(model, options) {
	if (model.kind === "gltf") {
		return buildGltfGeometry(model, options);
	}

	const positions = [];
	const uvs = [];
	const normals = [];
	const resolution = model.resolution || { width: 16, height: 16 };
	const meshElements = model.elements.filter((element) => element.type === "mesh");

	for (const element of meshElements) {
		const vertices = element.vertices || {};
		const smoothNormals = options.normalMode === "smooth" && element.shading === "smooth"
			? calculateSmoothNormals(vertices, element.faces || {})
			: new Map();
		for (const face of Object.values(element.faces || {})) {
			const ids = face.vertices || [];
			if (ids.length !== 3) {
				continue;
			}
			const faceNormal = normalFor(vertices, ids);
			for (const id of ids) {
				const position = vertices[id];
				const uv = face.uv?.[id];
				if (!position || !uv) {
					continue;
				}
				positions.push(
					position[0] * MODEL_UNIT,
					position[1] * MODEL_UNIT,
					position[2] * MODEL_UNIT
				);
				const u = uv[0] / resolution.width;
				const rawV = uv[1] / resolution.height;
				uvs.push(u, options.uvMode === "three" ? 1 - rawV : rawV);
				const normal = smoothNormals.get(id) || faceNormal;
				normals.push(normal.x, normal.y, normal.z);
			}
		}
	}

	const geometry = new THREE.BufferGeometry();
	geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
	geometry.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
	geometry.setAttribute("normal", new THREE.Float32BufferAttribute(normals, 3));
	geometry.computeBoundingBox();
	geometry.computeBoundingSphere();
	return geometry;
}

function buildGltfGeometry(model, options) {
	const positions = [];
	const uvs = [];
	const normals = [];
	for (const index of model.indices) {
		const positionOffset = index * 3;
		const uvOffset = index * 2;
		positions.push(
			model.positions[positionOffset],
			model.positions[positionOffset + 1],
			model.positions[positionOffset + 2]
		);
		const rawV = model.uvs[uvOffset + 1];
		uvs.push(model.uvs[uvOffset], options.uvMode === "three" ? 1 - rawV : rawV);
		normals.push(
			model.normals[positionOffset],
			model.normals[positionOffset + 1],
			model.normals[positionOffset + 2]
		);
	}
	const geometry = new THREE.BufferGeometry();
	geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
	geometry.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
	geometry.setAttribute("normal", new THREE.Float32BufferAttribute(normals, 3));
	geometry.computeBoundingBox();
	geometry.computeBoundingSphere();
	return geometry;
}

function parseGlbModel(buffer) {
	const view = new DataView(buffer);
	if (view.byteLength < 12 || view.getUint32(0, true) !== 0x46546c67 || view.getUint32(4, true) !== 2) {
		throw new Error("Preview model is not a GLB v2 file");
	}
	const declaredLength = view.getUint32(8, true);
	if (declaredLength !== view.byteLength) {
		throw new Error("Preview GLB length does not match its header");
	}

	let root = null;
	let bin = null;
	let offset = 12;
	while (offset < view.byteLength) {
		const chunkLength = view.getUint32(offset, true);
		const chunkType = view.getUint32(offset + 4, true);
		const chunkStart = offset + 8;
		const chunkEnd = chunkStart + chunkLength;
		if (chunkType === 0x4e4f534a) {
			const text = new TextDecoder().decode(new Uint8Array(buffer, chunkStart, chunkLength)).trim();
			root = JSON.parse(text);
		} else if (chunkType === 0x004e4942) {
			bin = buffer.slice(chunkStart, chunkEnd);
		}
		offset = chunkEnd;
	}
	if (!root || !bin) {
		throw new Error("Preview GLB must include JSON and BIN chunks");
	}
	return extractGltfMesh(root, bin);
}

function extractGltfMesh(root, bin) {
	const meshIndices = sceneMeshIndices(root);
	const mesh = root.meshes[meshIndices[0]];
	const primitive = mesh.primitives.find((entry) => (entry.mode ?? 4) === 4);
	if (!primitive) {
		throw new Error("Preview GLB does not contain triangle primitives");
	}
	return {
		kind: "gltf",
		positions: readFloatAccessor(root, bin, primitive.attributes.POSITION, 3),
		normals: readFloatAccessor(root, bin, primitive.attributes.NORMAL, 3),
		uvs: readFloatAccessor(root, bin, primitive.attributes.TEXCOORD_0, 2),
		indices: readIndexAccessor(root, bin, primitive.indices)
	};
}

function sceneMeshIndices(root) {
	const result = [];
	const scene = root.scenes[root.scene ?? 0];
	for (const nodeIndex of scene.nodes || []) {
		collectMeshIndices(root.nodes, nodeIndex, result);
	}
	return result;
}

function collectMeshIndices(nodes, nodeIndex, result) {
	const node = nodes[nodeIndex];
	if (node.mesh != null) {
		result.push(node.mesh);
	}
	for (const child of node.children || []) {
		collectMeshIndices(nodes, child, result);
	}
}

function readFloatAccessor(root, bin, accessorIndex, width) {
	const accessor = root.accessors[accessorIndex];
	const view = root.bufferViews[accessor.bufferView];
	if (accessor.componentType !== 5126) {
		throw new Error("Preview GLB only supports float mesh accessors");
	}
	const stride = view.byteStride || width * 4;
	const data = new DataView(bin);
	const values = new Float32Array(accessor.count * width);
	const start = (view.byteOffset || 0) + (accessor.byteOffset || 0);
	for (let index = 0; index < accessor.count; index++) {
		const offset = start + index * stride;
		for (let component = 0; component < width; component++) {
			values[index * width + component] = data.getFloat32(offset + component * 4, true);
		}
	}
	return values;
}

function readIndexAccessor(root, bin, accessorIndex) {
	const accessor = root.accessors[accessorIndex];
	const view = root.bufferViews[accessor.bufferView];
	const data = new DataView(bin);
	const componentSize = accessor.componentType === 5121 ? 1 : accessor.componentType === 5123 ? 2 : 4;
	const stride = view.byteStride || componentSize;
	const values = new Uint32Array(accessor.count);
	const start = (view.byteOffset || 0) + (accessor.byteOffset || 0);
	for (let index = 0; index < accessor.count; index++) {
		const offset = start + index * stride;
		if (accessor.componentType === 5121) {
			values[index] = data.getUint8(offset);
		} else if (accessor.componentType === 5123) {
			values[index] = data.getUint16(offset, true);
		} else if (accessor.componentType === 5125) {
			values[index] = data.getUint32(offset, true);
		} else {
			throw new Error("Preview GLB uses an unsupported index component type");
		}
	}
	return values;
}

function calculateSmoothNormals(vertices, faces) {
	const sums = new Map();
	const counts = new Map();
	for (const face of Object.values(faces)) {
		const ids = face.vertices || [];
		if (ids.length !== 3) {
			continue;
		}
		const normal = normalFor(vertices, ids);
		for (const id of ids) {
			if (!sums.has(id)) {
				sums.set(id, new THREE.Vector3());
				counts.set(id, 0);
			}
			sums.get(id).add(normal);
			counts.set(id, counts.get(id) + 1);
		}
	}
	for (const [id, sum] of sums) {
		sum.divideScalar(counts.get(id));
		if (sum.lengthSq() > 1e-8) {
			sum.normalize();
		}
	}
	return sums;
}

function normalFor(vertices, ids) {
	const a = vectorFrom(vertices[ids[0]]);
	const b = vectorFrom(vertices[ids[1]]);
	const c = vectorFrom(vertices[ids[2]]);
	const normal = b.clone().sub(a).cross(c.clone().sub(a));
	if (normal.lengthSq() < 1e-8) {
		return new THREE.Vector3(0, 1, 0);
	}
	return normal.normalize();
}

function vectorFrom(position) {
	return new THREE.Vector3(
		position[0] * MODEL_UNIT,
		position[1] * MODEL_UNIT,
		position[2] * MODEL_UNIT
	);
}

function updateScene() {
	if (!mesh) {
		return;
	}
	updateLabels();
	wire.visible = controls.showWire.checked;
	playerGroup.visible = controls.showPlayer.checked && controls.viewMode.value !== "raw" && controls.viewMode.value !== "blockbench";
	grid.visible = controls.showGrid.checked;

	itemRoot.position.set(0, 0, 0);
	itemRoot.rotation.set(0, 0, 0);
	itemRoot.scale.setScalar(1);
	meshGroup.position.set(0, 0, 0);
	meshGroup.rotation.set(0, 0, 0);
	meshGroup.scale.setScalar(1);

	applyViewPlacement();
	applyDisplayTransform();
	applyManualTransform();
	updateStats();
	updateOverlay();
}

function applyViewPlacement() {
	const mode = controls.viewMode.value;
	if (mode === "raw") {
		itemRoot.position.set(0, 0.85, 0);
		orbit.target.set(0, 1.2, 0);
		camera.position.lerp(new THREE.Vector3(3.6, 2.7, 6.2), 1);
		return;
	}
	if (mode === "blockbench") {
		itemRoot.position.set(0, 0.25, 0);
		itemRoot.rotation.set(0, 0, 0);
		orbit.target.set(0, 1.4, 0);
		camera.position.lerp(new THREE.Vector3(3.2, 2.6, 6.2), 1);
		return;
	}
	if (mode === "firstperson") {
		itemRoot.position.set(-0.22, 1.18, 0.95);
		orbit.target.set(0.15, 1.38, 0.35);
		camera.position.lerp(new THREE.Vector3(0.25, 1.95, 4.1), 1);
		return;
	}
	itemRoot.position.set(-1.02, 1.42, 0.86);
	orbit.target.set(-0.2, 1.42, 0.15);
	camera.position.lerp(new THREE.Vector3(3.35, 2.45, 5.35), 1);
}

function applyDisplayTransform() {
	if (controls.viewMode.value === "raw" || controls.viewMode.value === "blockbench") {
		centerRawMesh();
		return;
	}
	const preset = selectedDisplayPreset();
	if (!preset) {
		return;
	}
	const rotation = preset.rotation || [0, 0, 0];
	const translation = preset.translation || [0, 0, 0];
	const scale = preset.scale || [1, 1, 1];
	meshGroup.rotation.set(
		THREE.MathUtils.degToRad(rotation[0] || 0),
		THREE.MathUtils.degToRad(rotation[1] || 0),
		THREE.MathUtils.degToRad(rotation[2] || 0)
	);
	meshGroup.position.set(
		(translation[0] || 0) / 16,
		(translation[1] || 0) / 16,
		(translation[2] || 0) / 16
	);
	meshGroup.scale.set(scale[0] || 1, scale[1] || 1, scale[2] || 1);
}

function selectedDisplayPreset() {
	const key = controls.displayPreset.value;
	if (key.startsWith("throwing:")) {
		return throwingItemModel.display?.[key.slice("throwing:".length)];
	}
	return baseItemModel.display?.[key];
}

function centerRawMesh() {
	if (!mesh.geometry.boundingBox) {
		return;
	}
	const center = new THREE.Vector3();
	mesh.geometry.boundingBox.getCenter(center);
	meshGroup.position.copy(center.multiplyScalar(-1));
	meshGroup.position.y += 1.2;
	meshGroup.scale.setScalar(0.72);
}

function applyManualTransform() {
	const manualScale = Number(controls.scale.value) / 100;
	itemRoot.rotation.x += THREE.MathUtils.degToRad(Number(controls.rotX.value));
	itemRoot.rotation.y += THREE.MathUtils.degToRad(Number(controls.rotY.value));
	itemRoot.rotation.z += THREE.MathUtils.degToRad(Number(controls.rotZ.value));
	itemRoot.scale.multiplyScalar(manualScale);
	itemRoot.position.x += Number(controls.posX.value) / 100;
	itemRoot.position.y += Number(controls.posY.value) / 100;
	itemRoot.position.z += Number(controls.posZ.value) / 100;
}

function updateLabels() {
	for (const key of Object.keys(labels)) {
		labels[key].textContent = controls[key].value;
	}
}

function updateOverlay() {
	overlayMode.textContent = [
		controls.uvMode.selectedOptions[0].textContent,
		controls.textureFlip.selectedOptions[0].textContent,
		controls.filterMode.selectedOptions[0].textContent,
		controls.wrapMode.selectedOptions[0].textContent,
		controls.normalMode.selectedOptions[0].textContent
	].join(" / ");
}

function collectStats(model, geometry) {
	if (model.kind === "gltf") {
		const uvMin = [Infinity, Infinity];
		const uvMax = [-Infinity, -Infinity];
		for (let index = 0; index < model.uvs.length; index += 2) {
			uvMin[0] = Math.min(uvMin[0], model.uvs[index]);
			uvMin[1] = Math.min(uvMin[1], model.uvs[index + 1]);
			uvMax[0] = Math.max(uvMax[0], model.uvs[index]);
			uvMax[1] = Math.max(uvMax[1], model.uvs[index + 1]);
		}
		return {
			vertices: model.positions.length / 3,
			faces: model.indices.length / 3,
			triangles: geometry.attributes.position.count / 3,
			uvMin,
			uvMax
		};
	}

	let faceCount = 0;
	let vertexCount = 0;
	let uvMin = [Infinity, Infinity];
	let uvMax = [-Infinity, -Infinity];
	for (const element of model.elements || []) {
		if (element.type !== "mesh") {
			continue;
		}
		vertexCount += Object.keys(element.vertices || {}).length;
		for (const face of Object.values(element.faces || {})) {
			faceCount++;
			for (const id of face.vertices || []) {
				const uv = face.uv?.[id];
				if (!uv) {
					continue;
				}
				uvMin[0] = Math.min(uvMin[0], uv[0]);
				uvMin[1] = Math.min(uvMin[1], uv[1]);
				uvMax[0] = Math.max(uvMax[0], uv[0]);
				uvMax[1] = Math.max(uvMax[1], uv[1]);
			}
		}
	}
	return {
		vertices: vertexCount,
		faces: faceCount,
		triangles: geometry.attributes.position.count / 3,
		uvMin,
		uvMax
	};
}

function updateStats() {
	if (!rawStats || !texture) {
		return;
	}
	const bbox = mesh.geometry.boundingBox;
	const size = bbox ? bbox.getSize(new THREE.Vector3()) : new THREE.Vector3();
	const rows = {
		Texture: `${texture.image.width} x ${texture.image.height}`,
		Vertices: rawStats.vertices,
		Faces: rawStats.faces,
		Triangles: rawStats.triangles,
		"UV min": `${rawStats.uvMin[0].toFixed(2)}, ${rawStats.uvMin[1].toFixed(2)}`,
		"UV max": `${rawStats.uvMax[0].toFixed(2)}, ${rawStats.uvMax[1].toFixed(2)}`,
		"Mesh size": `${size.x.toFixed(2)}, ${size.y.toFixed(2)}, ${size.z.toFixed(2)}`,
		Preset: controls.displayPreset.value
	};
	statsNode.replaceChildren();
	for (const [key, value] of Object.entries(rows)) {
		const dt = document.createElement("dt");
		dt.textContent = key;
		const dd = document.createElement("dd");
		dd.textContent = String(value);
		statsNode.append(dt, dd);
	}
}

function buildPlayer() {
	const skin = {
		head: 0x8d6245,
		hair: 0x4d2418,
		body: 0x3e7a5f,
		sleeve: 0x5d8a6d,
		arm: 0xb8906e,
		leg: 0x4d3024
	};
	const body = block(0.72, 1.05, 0.36, skin.body);
	body.position.set(0, 1.15, 0);
	const head = block(0.72, 0.72, 0.72, skin.head);
	head.position.set(0, 2.08, 0);
	const hair = block(0.76, 0.16, 0.76, skin.hair);
	hair.position.set(0, 2.44, 0);
	const leftArm = block(0.34, 1.02, 0.34, skin.arm);
	leftArm.position.set(0.62, 1.15, 0);
	const rightArm = block(0.34, 1.02, 0.34, skin.arm);
	rightArm.position.set(-0.62, 1.15, 0);
	rightArm.rotation.z = THREE.MathUtils.degToRad(8);
	const leftLeg = block(0.34, 1.08, 0.34, skin.leg);
	leftLeg.position.set(0.19, 0.26, 0);
	const rightLeg = block(0.34, 1.08, 0.34, skin.leg);
	rightLeg.position.set(-0.19, 0.26, 0);
	playerGroup.add(body, head, hair, leftArm, rightArm, leftLeg, rightLeg);
}

function block(width, height, depth, color) {
	const geometry = new THREE.BoxGeometry(width, height, depth);
	const material = new THREE.MeshLambertMaterial({ color });
	return new THREE.Mesh(geometry, material);
}

function animate(time) {
	requestAnimationFrame(animate);
	const delta = Math.min(0.05, (time - lastTime) / 1000 || 0);
	lastTime = time;
	if (controls.autoRotate.checked) {
		itemRoot.rotation.y += delta * 0.8;
	}
	resize();
	orbit.update();
	renderer.render(scene, camera);
}

function resize() {
	const width = canvas.clientWidth;
	const height = canvas.clientHeight;
	if (canvas.width !== width || canvas.height !== height) {
		renderer.setSize(width, height, false);
		camera.aspect = width / Math.max(1, height);
		camera.updateProjectionMatrix();
	}
}

function disposeObject(object) {
	if (!object) {
		return;
	}
	object.traverse?.((child) => {
		child.geometry?.dispose();
		if (Array.isArray(child.material)) {
			child.material.forEach((material) => material.dispose());
		} else {
			child.material?.dispose();
		}
	});
}
