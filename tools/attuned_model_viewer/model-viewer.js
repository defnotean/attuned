const THREE = window.THREE;
if (!THREE) {
  throw new Error("Three.js failed to load before model-viewer.js");
}

const ASSET_ROOT = "/src/main/resources/assets";
const CACHE_BUST = String(Date.now());
const MODELS = [
  { id: "attuned:block/attunement_altar_none", label: "Attunement Altar - None", peer: "attuned:block/altar_of_reweaving" },
  { id: "attuned:block/attunement_altar_fury", label: "Attunement Altar - Fury", peer: "attuned:block/altar_of_reweaving" },
  { id: "attuned:block/attunement_altar_bastion", label: "Attunement Altar - Bastion", peer: "attuned:block/altar_of_reweaving" },
  { id: "attuned:block/attunement_altar_zephyr", label: "Attunement Altar - Zephyr", peer: "attuned:block/altar_of_reweaving" },
  { id: "attuned:block/attunement_altar_holy", label: "Attunement Altar - Holy", peer: "attuned:block/altar_of_reweaving" },
  { id: "attuned:block/altar_of_reweaving", label: "Altar of Reweaving", peer: "attuned:block/attunement_altar_holy" }
];

const FACE_ORDER = ["north", "south", "east", "west", "up", "down"];
const DEFAULT_UV = [0, 0, 16, 16];
const modelCache = new Map();
const textureCache = new Map();
const materialCache = new Map();
const imageCache = new Map();

const canvas = document.querySelector("#viewer");
const modelSelect = document.querySelector("#modelSelect");
const singleMode = document.querySelector("#singleMode");
const pairMode = document.querySelector("#pairMode");
const resetCameraButton = document.querySelector("#resetCamera");
const saveShotButton = document.querySelector("#saveShot");
const statusDot = document.querySelector("#statusDot");
const diagnostics = document.querySelector("#diagnostics");
const textureList = document.querySelector("#textureList");
const cameraHud = document.querySelector("#cameraHud");
const assetHud = document.querySelector("#assetHud");

const statElements = {
  elementCount: document.querySelector("#elementCount"),
  faceCount: document.querySelector("#faceCount"),
  textureCount: document.querySelector("#textureCount"),
  scaleReadout: document.querySelector("#scaleReadout")
};

const renderer = new THREE.WebGLRenderer({
  canvas,
  antialias: true,
  alpha: false,
  preserveDrawingBuffer: true
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
if ("outputColorSpace" in renderer && THREE.SRGBColorSpace) {
  renderer.outputColorSpace = THREE.SRGBColorSpace;
} else if ("outputEncoding" in renderer && THREE.sRGBEncoding) {
  renderer.outputEncoding = THREE.sRGBEncoding;
}
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x101116);
scene.fog = new THREE.Fog(0x101116, 5, 14);

const camera = new THREE.PerspectiveCamera(42, 1, 0.05, 100);
camera.position.set(2.65, 2.05, 3.15);

const controls = createOrbitController(camera, renderer.domElement);
controls.target.set(0, 0.7, 0);
controls.minDistance = 1.8;
controls.maxDistance = 8;
controls.maxPolarAngle = Math.PI * 0.49;

const root = new THREE.Group();
scene.add(root);

const floor = new THREE.GridHelper(8, 16, 0x3a4650, 0x222830);
floor.position.y = -0.005;
scene.add(floor);

const ambient = new THREE.HemisphereLight(0xbfd3e8, 0x17100c, 1.05);
scene.add(ambient);

const keyLight = new THREE.DirectionalLight(0xfff3d0, 2.15);
keyLight.position.set(3.5, 6, 2.6);
keyLight.castShadow = true;
keyLight.shadow.mapSize.set(1024, 1024);
scene.add(keyLight);

const rimLight = new THREE.DirectionalLight(0x74e7ff, 1.05);
rimLight.position.set(-4, 3.2, -2.2);
scene.add(rimLight);

const fillLight = new THREE.PointLight(0x9d72da, 0.75, 5);
fillLight.position.set(-1.2, 1.3, 1.6);
scene.add(fillLight);

const state = {
  layout: "pair",
  currentModel: "attuned:block/attunement_altar_holy",
  summary: null,
  ready: false,
  errors: []
};

window.__attunedViewerState = state;

for (const model of MODELS) {
  const option = document.createElement("option");
  option.value = model.id;
  option.textContent = model.label;
  if (model.id === state.currentModel) {
    option.selected = true;
  }
  modelSelect.append(option);
}

modelSelect.addEventListener("change", () => {
  state.currentModel = modelSelect.value;
  renderSelection();
});

singleMode.addEventListener("click", () => {
  state.layout = "single";
  updateModeButtons();
  renderSelection();
});

pairMode.addEventListener("click", () => {
  state.layout = "pair";
  updateModeButtons();
  renderSelection();
});

resetCameraButton.addEventListener("click", resetCamera);
saveShotButton.addEventListener("click", saveScreenshot);
window.addEventListener("resize", resize);

resize();
resetCamera();
renderSelection();
animate();

async function renderSelection() {
  setStatus("loading");
  clearRoot();
  state.errors = [];

  try {
    const selected = MODELS.find((model) => model.id === state.currentModel) || MODELS[0];
    const loadList = state.layout === "pair"
      ? [
          { id: selected.id, x: -0.68, label: selected.label },
          { id: selected.peer, x: 0.68, label: labelForModel(selected.peer) }
        ]
      : [{ id: selected.id, x: 0, label: selected.label }];

    const summaries = [];
    for (const item of loadList) {
      const { group, summary } = await buildModelGroup(item.id);
      group.position.x = item.x;
      group.name = item.label;
      root.add(group);
      summaries.push(summary);
    }

    fitRoot();
    fitCameraToRoot();
    state.summary = mergeSummaries(summaries);
    state.ready = true;
    updateStats(state.summary);
    updateTextureList(state.summary.textures);
    updateDiagnostics(state.summary);
    setStatus("ready");
  } catch (error) {
    state.ready = false;
    state.errors.push(error.message);
    diagnostics.classList.add("error");
    diagnostics.textContent = error.stack || error.message;
    setStatus("error");
  }
}

async function buildModelGroup(modelId) {
  const resolved = await resolveModel(modelId);
  const group = new THREE.Group();
  group.name = modelId;

  let faceCount = 0;
  const textureIds = new Set();

  for (const element of resolved.elements) {
    for (const faceName of FACE_ORDER) {
      const face = element.faces?.[faceName];
      if (!face) {
        continue;
      }

      const textureId = resolveTextureReference(resolved.textures, face.texture);
      if (!textureId) {
        state.errors.push(`${modelId}: ${element.name || "element"} ${faceName} has unresolved texture ${face.texture}`);
        continue;
      }

      textureIds.add(textureId);
      const geometry = createFaceGeometry(element, faceName, face.uv || DEFAULT_UV, face.rotation || 0);
      const material = await materialForTexture(textureId);
      const mesh = new THREE.Mesh(geometry, material);
      mesh.name = `${element.name || "element"}:${faceName}`;
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      group.add(mesh);
      faceCount += 1;
    }
  }

  const summary = {
    modelId,
    elements: resolved.elements.length,
    faces: faceCount,
    textures: [...textureIds].sort(),
    warnings: [...state.errors]
  };

  return { group, summary };
}

async function resolveModel(modelId, stack = []) {
  if (modelCache.has(modelId)) {
    return modelCache.get(modelId);
  }

  if (modelId.startsWith("minecraft:")) {
    const minecraftBase = { id: modelId, textures: {}, elements: [] };
    modelCache.set(modelId, minecraftBase);
    return minecraftBase;
  }

  if (stack.includes(modelId)) {
    throw new Error(`Model parent cycle: ${[...stack, modelId].join(" -> ")}`);
  }

  const raw = await fetchJson(modelUrl(modelId));
  const parent = raw.parent ? await resolveModel(raw.parent, [...stack, modelId]) : { textures: {}, elements: [] };
  const resolved = {
    id: modelId,
    textures: { ...parent.textures, ...(raw.textures || {}) },
    elements: raw.elements || parent.elements || []
  };
  modelCache.set(modelId, resolved);
  return resolved;
}

function resolveTextureReference(textures, reference) {
  let current = reference;
  const seen = new Set();

  for (let i = 0; i < 16; i += 1) {
    if (!current) {
      return null;
    }

    if (!current.startsWith("#")) {
      return current;
    }

    const key = current.slice(1);
    if (seen.has(key)) {
      return null;
    }

    seen.add(key);
    current = textures[key];
  }

  return null;
}

function createFaceGeometry(element, faceName, uv, rotation) {
  const [x1, y1, z1] = element.from;
  const [x2, y2, z2] = element.to;
  const corners = {
    north: [[x2, y1, z1], [x1, y1, z1], [x1, y2, z1], [x2, y2, z1]],
    south: [[x1, y1, z2], [x2, y1, z2], [x2, y2, z2], [x1, y2, z2]],
    east: [[x2, y1, z2], [x2, y1, z1], [x2, y2, z1], [x2, y2, z2]],
    west: [[x1, y1, z1], [x1, y1, z2], [x1, y2, z2], [x1, y2, z1]],
    up: [[x1, y2, z1], [x1, y2, z2], [x2, y2, z2], [x2, y2, z1]],
    down: [[x1, y1, z2], [x1, y1, z1], [x2, y1, z1], [x2, y1, z2]]
  }[faceName];

  const converted = corners.map((corner) => convertPoint(corner, element.rotation));
  const positions = [
    ...converted[0], ...converted[1], ...converted[2],
    ...converted[0], ...converted[2], ...converted[3]
  ];
  const faceUvs = expandUvs(uv, rotation);
  const uvs = [
    ...faceUvs[0], ...faceUvs[1], ...faceUvs[2],
    ...faceUvs[0], ...faceUvs[2], ...faceUvs[3]
  ];

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(new Float32Array(positions), 3));
  geometry.setAttribute("uv", new THREE.BufferAttribute(new Float32Array(uvs), 2));
  geometry.computeVertexNormals();
  return geometry;
}

function convertPoint(point, rotation) {
  const vector = toThreeVector(point);
  if (!rotation) {
    return vector.toArray();
  }

  const origin = toThreeVector(rotation.origin || [8, 8, 8]);
  const axis = new THREE.Vector3(
    rotation.axis === "x" ? 1 : 0,
    rotation.axis === "y" ? 1 : 0,
    rotation.axis === "z" ? 1 : 0
  );
  vector.sub(origin).applyAxisAngle(axis, THREE.MathUtils.degToRad(rotation.angle || 0)).add(origin);
  return vector.toArray();
}

function toThreeVector(point) {
  return new THREE.Vector3(
    (point[0] - 8) / 16,
    point[1] / 16,
    (point[2] - 8) / 16
  );
}

function expandUvs(uv, rotation) {
  const [u1, v1, u2, v2] = uv;
  const top = 1 - v1 / 16;
  const bottom = 1 - v2 / 16;
  const corners = [
    [u1 / 16, bottom],
    [u2 / 16, bottom],
    [u2 / 16, top],
    [u1 / 16, top]
  ];

  const steps = (((rotation / 90) % 4) + 4) % 4;
  for (let i = 0; i < steps; i += 1) {
    corners.unshift(corners.pop());
  }

  return corners;
}

async function materialForTexture(textureId) {
  if (materialCache.has(textureId)) {
    return materialCache.get(textureId);
  }

  const texture = await loadMinecraftTexture(textureId);
  const isGem = /(_gem|gem_)/.test(textureId);
  const isTop = /(_top|top_)/.test(textureId);
  const material = new THREE.MeshStandardMaterial({
    map: texture,
    transparent: true,
    alphaTest: 0.06,
    roughness: 0.72,
    metalness: 0.02,
    side: THREE.DoubleSide,
    emissive: isGem ? new THREE.Color(0x2abfcc) : isTop ? new THREE.Color(0x182d35) : new THREE.Color(0x000000),
    emissiveIntensity: isGem ? 0.1 : isTop ? 0.015 : 0
  });
  materialCache.set(textureId, material);
  return material;
}

async function loadMinecraftTexture(textureId) {
  if (textureCache.has(textureId)) {
    return textureCache.get(textureId);
  }

  const image = await loadImage(textureUrl(textureId));
  const frameSize = Math.min(image.width, image.height);
  const canvasFrame = document.createElement("canvas");
  canvasFrame.width = frameSize;
  canvasFrame.height = frameSize;
  const context = canvasFrame.getContext("2d", { alpha: true });
  context.imageSmoothingEnabled = false;
  context.clearRect(0, 0, frameSize, frameSize);
  context.drawImage(image, 0, 0, frameSize, frameSize, 0, 0, frameSize, frameSize);

  const texture = new THREE.CanvasTexture(canvasFrame);
  if ("colorSpace" in texture && THREE.SRGBColorSpace) {
    texture.colorSpace = THREE.SRGBColorSpace;
  } else if ("encoding" in texture && THREE.sRGBEncoding) {
    texture.encoding = THREE.sRGBEncoding;
  }
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.generateMipmaps = false;
  texture.wrapS = THREE.ClampToEdgeWrapping;
  texture.wrapT = THREE.ClampToEdgeWrapping;
  textureCache.set(textureId, texture);
  return texture;
}

async function loadImage(url) {
  if (imageCache.has(url)) {
    return imageCache.get(url);
  }

  const promise = new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Unable to load texture ${url}`));
    image.src = url;
  });
  imageCache.set(url, promise);
  return promise;
}

async function fetchJson(url) {
  const text = await requestText(url);
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`Unable to parse ${url}: ${error.message}`);
  }
}

function requestText(url) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.overrideMimeType("application/json");
    request.open("GET", url, true);
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        resolve(request.responseText);
      } else {
        reject(new Error(`Unable to load ${url}: ${request.status} ${request.statusText}`));
      }
    };
    request.onerror = () => reject(new Error(`Unable to load ${url}`));
    request.send();
  });
}

function modelUrl(modelId) {
  const asset = parseAssetId(modelId);
  return `${ASSET_ROOT}/${asset.namespace}/models/${asset.path}.json`;
}

function textureUrl(textureId) {
  const asset = parseAssetId(textureId);
  return `${ASSET_ROOT}/${asset.namespace}/textures/${asset.path}.png?v=${CACHE_BUST}`;
}

function parseAssetId(id) {
  const [maybeNamespace, ...pathParts] = id.split(":");
  if (pathParts.length === 0) {
    return { namespace: "minecraft", path: maybeNamespace };
  }
  return { namespace: maybeNamespace, path: pathParts.join(":") };
}

function clearRoot() {
  while (root.children.length > 0) {
    const child = root.children.pop();
    child.traverse((node) => {
      if (node.geometry) {
        node.geometry.dispose();
      }
    });
  }
}

function fitRoot() {
  const box = new THREE.Box3().setFromObject(root);
  const size = new THREE.Vector3();
  const center = new THREE.Vector3();
  box.getSize(size);
  box.getCenter(center);

  root.position.x -= center.x;
  root.position.z -= center.z;
  root.position.y -= box.min.y;

  const widest = Math.max(size.x, size.z, size.y * 0.72);
  const scale = state.layout === "pair" ? Math.min(1.1, 2.25 / Math.max(widest, 0.1)) : Math.min(1.38, 1.72 / Math.max(widest, 0.1));
  root.scale.setScalar(scale);
  statElements.scaleReadout.textContent = scale.toFixed(2);
}

function fitCameraToRoot() {
  if (root.children.length === 0) {
    resetCamera();
    return;
  }

  const box = new THREE.Box3().setFromObject(root);
  const size = new THREE.Vector3();
  const center = new THREE.Vector3();
  box.getSize(size);
  box.getCenter(center);

  const verticalFov = THREE.MathUtils.degToRad(camera.fov);
  const horizontalFov = 2 * Math.atan(Math.tan(verticalFov / 2) * Math.max(camera.aspect, 0.1));
  const distanceForHeight = (size.y * 0.62) / Math.tan(verticalFov / 2);
  const distanceForWidth = (size.x * 0.62) / Math.tan(horizontalFov / 2);
  const distance = Math.max(distanceForHeight, distanceForWidth, 3.2) * 1.22;
  const direction = new THREE.Vector3(0.72, 0.48, 0.92).normalize();
  const position = center.clone().addScaledVector(direction, distance);
  controls.setView(position, center);
}

function mergeSummaries(summaries) {
  const merged = {
    modelId: summaries.map((summary) => summary.modelId).join(" + "),
    elements: 0,
    faces: 0,
    textures: [],
    warnings: []
  };
  const textures = new Set();
  const warnings = new Set();

  for (const summary of summaries) {
    merged.elements += summary.elements;
    merged.faces += summary.faces;
    for (const texture of summary.textures) {
      textures.add(texture);
    }
    for (const warning of summary.warnings) {
      warnings.add(warning);
    }
  }

  merged.textures = [...textures].sort();
  merged.warnings = [...warnings];
  return merged;
}

function updateStats(summary) {
  statElements.elementCount.textContent = String(summary.elements);
  statElements.faceCount.textContent = String(summary.faces);
  statElements.textureCount.textContent = String(summary.textures.length);
  assetHud.textContent = `${summary.elements} elements / ${summary.faces} faces`;
}

function updateTextureList(textures) {
  textureList.replaceChildren();
  for (const textureId of textures) {
    const row = document.createElement("div");
    row.className = "texture-row";
    const img = document.createElement("img");
    img.alt = "";
    img.src = textureUrl(textureId);
    const code = document.createElement("code");
    code.textContent = textureId;
    row.append(img, code);
    textureList.append(row);
  }
}

function updateDiagnostics(summary) {
  diagnostics.classList.toggle("error", summary.warnings.length > 0);
  diagnostics.textContent = [
    `model: ${summary.modelId}`,
    `textures: ${summary.textures.join(", ")}`,
    summary.warnings.length ? `warnings:\n${summary.warnings.map((warning) => `- ${warning}`).join("\n")}` : "warnings: none"
  ].join("\n");
}

function setStatus(status) {
  statusDot.classList.toggle("ready", status === "ready");
  statusDot.classList.toggle("error", status === "error");
  if (status === "loading") {
    assetHud.textContent = "Loading assets";
  }
}

function updateModeButtons() {
  singleMode.classList.toggle("active", state.layout === "single");
  pairMode.classList.toggle("active", state.layout === "pair");
}

function resetCamera() {
  if (root.children.length > 0) {
    fitCameraToRoot();
  } else {
    controls.setView(new THREE.Vector3(2.65, 2.05, 3.15), new THREE.Vector3(0, 0.72, 0));
  }
}

function saveScreenshot() {
  const link = document.createElement("a");
  link.download = "attuned-model-viewer.png";
  link.href = renderer.domElement.toDataURL("image/png");
  link.click();
}

function resize() {
  const width = canvas.clientWidth || window.innerWidth;
  const height = canvas.clientHeight || window.innerHeight;
  camera.aspect = width / Math.max(height, 1);
  camera.updateProjectionMatrix();
  renderer.setSize(width, height, false);
}

function animate() {
  controls.update();
  cameraHud.textContent = `Orbit ${camera.position.distanceTo(controls.target).toFixed(2)}`;
  renderer.render(scene, camera);
  requestAnimationFrame(animate);
}

function labelForModel(modelId) {
  return MODELS.find((model) => model.id === modelId)?.label || modelId;
}

function createOrbitController(camera, domElement) {
  const target = new THREE.Vector3();
  const spherical = new THREE.Spherical();
  const cameraOffset = new THREE.Vector3();
  const pointer = { active: false, x: 0, y: 0, mode: "rotate" };
  const controller = {
    target,
    minDistance: 1,
    maxDistance: 8,
    maxPolarAngle: Math.PI * 0.49,
    update: apply,
    setView(position, nextTarget) {
      target.copy(nextTarget);
      camera.position.copy(position);
      syncFromCamera();
      apply();
    }
  };

  syncFromCamera();

  domElement.addEventListener("contextmenu", (event) => event.preventDefault());
  domElement.addEventListener("pointerdown", (event) => {
    pointer.active = true;
    pointer.x = event.clientX;
    pointer.y = event.clientY;
    pointer.mode = event.button === 2 || event.shiftKey ? "pan" : "rotate";
    domElement.setPointerCapture?.(event.pointerId);
  });
  domElement.addEventListener("pointermove", (event) => {
    if (!pointer.active) {
      return;
    }

    const dx = event.clientX - pointer.x;
    const dy = event.clientY - pointer.y;
    pointer.x = event.clientX;
    pointer.y = event.clientY;

    if (pointer.mode === "pan") {
      pan(dx, dy);
    } else {
      spherical.theta -= dx * 0.006;
      spherical.phi = clamp(spherical.phi - dy * 0.006, 0.18, controller.maxPolarAngle);
    }

    apply();
  });
  domElement.addEventListener("pointerup", (event) => {
    pointer.active = false;
    domElement.releasePointerCapture?.(event.pointerId);
  });
  domElement.addEventListener("pointercancel", () => {
    pointer.active = false;
  });
  domElement.addEventListener("wheel", (event) => {
    event.preventDefault();
    spherical.radius = clamp(
      spherical.radius * Math.exp(event.deltaY * 0.001),
      controller.minDistance,
      controller.maxDistance
    );
    apply();
  }, { passive: false });

  function syncFromCamera() {
    cameraOffset.copy(camera.position).sub(target);
    spherical.setFromVector3(cameraOffset);
    spherical.radius = clamp(spherical.radius, controller.minDistance, controller.maxDistance);
    spherical.phi = clamp(spherical.phi, 0.18, controller.maxPolarAngle);
  }

  function apply() {
    cameraOffset.setFromSpherical(spherical);
    camera.position.copy(target).add(cameraOffset);
    camera.lookAt(target);
  }

  function pan(dx, dy) {
    const scale = spherical.radius * 0.00125;
    const right = new THREE.Vector3().setFromMatrixColumn(camera.matrix, 0);
    const up = new THREE.Vector3().setFromMatrixColumn(camera.matrix, 1);
    target.addScaledVector(right, -dx * scale);
    target.addScaledVector(up, dy * scale);
  }

  return controller;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}
