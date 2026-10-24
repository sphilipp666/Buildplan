import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { TransformControls } from 'three/addons/controls/TransformControls.js';
import { parseSpongeSchematic } from './nbt.js';
import { getBlockColor, isBlockTransparent } from './palette.js';
import { createSampleCottage, createTestCube } from './sample.js';

// Application State
const state = {
    projectId: 'bauplan_projekt',
    title: 'Demo Bauwerk',
    schematicFilename: 'bauplan_projekt.schem',
    owner: 'Philipp',
    assignedPlayers: ['Philipp'],
    status: 'IN_PROGRESS',
    origin: { x: 100, y: 64, z: -250 },
    rotation: 0, // 0, 90, 180, 270
    unlocked: true,
    schematicData: null,
    currentLayerY: null, // null = all layers
    uploadedFileBlob: null
};

// Three.js Core Globals
let scene, camera, renderer, orbitControls, transformControls;
let gridHelper, schematicGroup, originMarker;
let solidInstancedMesh = null;
let transparentInstancedMesh = null;
let bboxHelper = null;
let terrainMesh = null;

// DOM Elements
const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');
const inputId = document.getElementById('input-id');
const inputTitle = document.getElementById('input-title');
const inputOwner = document.getElementById('input-owner');
const inputStatus = document.getElementById('input-status');
const inputAssigned = document.getElementById('input-assigned');
const inputOriginX = document.getElementById('input-origin-x');
const inputOriginY = document.getElementById('input-origin-y');
const inputOriginZ = document.getElementById('input-origin-z');
const inputRotation = document.getElementById('input-rotation');
const inputUnlocked = document.getElementById('input-unlocked');
const btnExport = document.getElementById('btn-export');
const btnDownloadSchem = document.getElementById('btn-download-schem');
const btnSample = document.getElementById('btn-sample');
const btnTestCube = document.getElementById('btn-test-cube');
const btnSingleplayer = document.getElementById('btn-singleplayer');
const btnToggleTerrain = document.getElementById('btn-toggle-terrain');
const btnResetCam = document.getElementById('btn-reset-cam');

// Viewport Navigation & Livemap DOM
const tabVoxelView = document.getElementById('tab-voxel-view');
const tabLivemapView = document.getElementById('tab-livemap-view');
const tabSplitView = document.getElementById('tab-split-view');
const btnFlyBlueMap = document.getElementById('btn-fly-bluemap');
const viewportContainer = document.getElementById('viewport-container');
const canvasContainer = document.getElementById('canvas-container');
const livemapContainer = document.getElementById('livemap-container');
const bluemapIframe = document.getElementById('bluemap-iframe');
const mixedContentWarning = document.getElementById('mixed-content-warning');
const btnBannerOpenMap = document.getElementById('btn-banner-open-map');
const btnCopyMapUrl = document.getElementById('btn-copy-map-url');

// BlueMap Marker Modal DOM
const btnBlueMapModal = document.getElementById('btn-bluemap-modal');
const blueMapModal = document.getElementById('bluemap-modal');
const btnModalClose = document.getElementById('btn-modal-close');
const tabModalHocon = document.getElementById('tab-modal-hocon');
const tabModalJson = document.getElementById('tab-modal-json');
const modalCodeContent = document.getElementById('modal-code-content');
const btnModalCopy = document.getElementById('btn-modal-copy');
const btnModalDownload = document.getElementById('btn-modal-download');
let currentModalTab = 'hocon'; // 'hocon' or 'json'
const layerSlider = document.getElementById('layer-slider');
const layerValueBadge = document.getElementById('layer-value');
const infoCard = document.getElementById('info-card');
const infoFilename = document.getElementById('info-filename');
const infoDimensions = document.getElementById('info-dimensions');
const infoBlocks = document.getElementById('info-blocks');
const materialsTableBody = document.getElementById('materials-table-body');
const totalMaterialsBadge = document.getElementById('total-materials-count');

// Initialize App
window.addEventListener('DOMContentLoaded', () => {
    initThree();
    initEventListeners();
    animate();

    // Load Singleplayer World Data by default
    loadSingleplayerWorldData();
});

function initThree() {
    const container = document.getElementById('canvas-container');

    // 1. Scene
    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x13151b);
    scene.fog = new THREE.FogExp2(0x13151b, 0.007);

    // 2. Camera
    camera = new THREE.PerspectiveCamera(55, container.clientWidth / container.clientHeight, 0.1, 1000);
    camera.position.set(30, 25, 30);

    // 3. Renderer
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
    renderer.setSize(container.clientWidth, container.clientHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.shadowMap.enabled = false;
    container.appendChild(renderer.domElement);

    // 4. Lighting
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.85);
    scene.add(ambientLight);

    const dirLight1 = new THREE.DirectionalLight(0xfff7e6, 1.2);
    dirLight1.position.set(50, 80, 40);
    scene.add(dirLight1);

    const dirLight2 = new THREE.DirectionalLight(0x8fa8d6, 0.5);
    dirLight2.position.set(-50, 40, -40);
    scene.add(dirLight2);

    // 5. Grid Helper (Minecraft-like 1 unit = 1 block)
    const gridSize = 160;
    const gridDivisions = 160;
    gridHelper = new THREE.GridHelper(gridSize, gridDivisions, 0x4ade80, 0x272b38);
    gridHelper.position.y = -0.01;
    scene.add(gridHelper);

    // 6. Cardinal Directions & Origin Marker
    createGroundCompass();

    // 7. Schematic Group
    schematicGroup = new THREE.Group();
    scene.add(schematicGroup);

    // 8. Orbit Controls
    orbitControls = new OrbitControls(camera, renderer.domElement);
    orbitControls.enableDamping = true;
    orbitControls.dampingFactor = 0.08;
    orbitControls.maxPolarAngle = Math.PI / 2 + 0.05; // Don't go below ground
    orbitControls.target.set(0, 5, 0);

    // 9. Transform Controls (Gizmo at origin)
    transformControls = new TransformControls(camera, renderer.domElement);
    transformControls.size = 0.75;
    transformControls.translationSnap = 1.0; // Snap to 1 Minecraft block
    transformControls.setMode('translate');

    // Create a dummy origin anchor
    originMarker = new THREE.Mesh(
        new THREE.SphereGeometry(0.35, 16, 16),
        new THREE.MeshBasicMaterial({ color: 0x38bdf8, wireframe: true })
    );
    scene.add(originMarker);
    transformControls.attach(originMarker);
    scene.add(transformControls);

    transformControls.addEventListener('dragging-changed', (event) => {
        orbitControls.enabled = !event.value;
    });

    transformControls.addEventListener('change', () => {
        if (transformControls.dragging) {
            const pos = originMarker.position;
            state.origin.x = Math.round(pos.x);
            state.origin.y = Math.round(pos.y);
            state.origin.z = Math.round(pos.z);
            updateOriginInputs();
            applyPlacementTransform();
        }
    });

    // Window Resize
    window.addEventListener('resize', onWindowResize);
}

function createGroundCompass() {
    const compassGroup = new THREE.Group();

    // Axis indicator arrows
    const axesHelper = new THREE.AxesHelper(10);
    axesHelper.position.set(0, 0.05, 0);
    compassGroup.add(axesHelper);

    // Subtle direction rings
    const ringGeo = new THREE.RingGeometry(9.9, 10.0, 32);
    ringGeo.rotateX(-Math.PI / 2);
    const ringMat = new THREE.MeshBasicMaterial({ color: 0x3b82f6, side: THREE.DoubleSide, opacity: 0.3, transparent: true });
    const ring = new THREE.Mesh(ringGeo, ringMat);
    ring.position.y = 0.02;
    compassGroup.add(ring);

    scene.add(compassGroup);
}

function onWindowResize() {
    const container = document.getElementById('canvas-container');
    camera.aspect = container.clientWidth / container.clientHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(container.clientWidth, container.clientHeight);
}

function animate() {
    requestAnimationFrame(animate);
    orbitControls.update();
    renderer.render(scene, camera);
}

// ---------------------------------------------------------------------
// Voxel Building & Three.js Rendering
// ---------------------------------------------------------------------

function renderSchematicVoxels(schemData) {
    // Clean up existing meshes
    if (solidInstancedMesh) {
        schematicGroup.remove(solidInstancedMesh);
        solidInstancedMesh.geometry.dispose();
        solidInstancedMesh.material.dispose();
        solidInstancedMesh = null;
    }
    if (transparentInstancedMesh) {
        schematicGroup.remove(transparentInstancedMesh);
        transparentInstancedMesh.geometry.dispose();
        transparentInstancedMesh.material.dispose();
        transparentInstancedMesh = null;
    }
    if (bboxHelper) {
        schematicGroup.remove(bboxHelper);
        bboxHelper.geometry.dispose();
        bboxHelper.material.dispose();
        bboxHelper = null;
    }

    const voxels = schemData.voxels;
    if (!voxels || voxels.length === 0) return;

    // Partition solid and transparent voxels for optimal rendering
    const solidVoxels = [];
    const transparentVoxels = [];

    for (let i = 0; i < voxels.length; i++) {
        const v = voxels[i];
        if (isBlockTransparent(v.state)) {
            transparentVoxels.push(v);
        } else {
            solidVoxels.push(v);
        }
    }

    // Geometry shared across all voxels (1x1x1 Minecraft block)
    const boxGeometry = new THREE.BoxGeometry(1, 1, 1);

    // 1. Solid Instanced Mesh
    if (solidVoxels.length > 0) {
        const solidMaterial = new THREE.MeshLambertMaterial({
            vertexColors: true,
            roughness: 0.8
        });

        solidInstancedMesh = new THREE.InstancedMesh(boxGeometry, solidMaterial, solidVoxels.length);
        solidInstancedMesh.userData = { voxels: solidVoxels };

        const dummy = new THREE.Object3D();
        const color = new THREE.Color();

        for (let i = 0; i < solidVoxels.length; i++) {
            const v = solidVoxels[i];
            dummy.position.set(v.x + 0.5, v.y + 0.5, v.z + 0.5);
            dummy.updateMatrix();
            solidInstancedMesh.setMatrixAt(i, dummy.matrix);

            const hex = getBlockColor(v.state);
            color.setHex(hex);
            solidInstancedMesh.setColorAt(i, color);
        }

        solidInstancedMesh.instanceMatrix.needsUpdate = true;
        if (solidInstancedMesh.instanceColor) solidInstancedMesh.instanceColor.needsUpdate = true;
        schematicGroup.add(solidInstancedMesh);
    }

    // 2. Transparent Instanced Mesh (Glass, Water)
    if (transparentVoxels.length > 0) {
        const transMaterial = new THREE.MeshLambertMaterial({
            vertexColors: true,
            transparent: true,
            opacity: 0.65,
            depthWrite: false
        });

        transparentInstancedMesh = new THREE.InstancedMesh(boxGeometry, transMaterial, transparentVoxels.length);
        transparentInstancedMesh.userData = { voxels: transparentVoxels };

        const dummy = new THREE.Object3D();
        const color = new THREE.Color();

        for (let i = 0; i < transparentVoxels.length; i++) {
            const v = transparentVoxels[i];
            dummy.position.set(v.x + 0.5, v.y + 0.5, v.z + 0.5);
            dummy.updateMatrix();
            transparentInstancedMesh.setMatrixAt(i, dummy.matrix);

            const hex = getBlockColor(v.state);
            color.setHex(hex);
            transparentInstancedMesh.setColorAt(i, color);
        }

        transparentInstancedMesh.instanceMatrix.needsUpdate = true;
        if (transparentInstancedMesh.instanceColor) transparentInstancedMesh.instanceColor.needsUpdate = true;
        schematicGroup.add(transparentInstancedMesh);
    }

    // 3. Bounding Box Outline
    const bboxGeo = new THREE.BoxGeometry(schemData.width, schemData.height, schemData.length);
    const wireGeo = new THREE.WireframeGeometry(bboxGeo);
    bboxHelper = new THREE.LineSegments(wireGeo, new THREE.LineBasicMaterial({ color: 0x4ade80, opacity: 0.4, transparent: true }));
    bboxHelper.position.set(schemData.width / 2, schemData.height / 2, schemData.length / 2);
    schematicGroup.add(bboxHelper);

    // Configure Layer Slider
    layerSlider.max = schemData.height - 1;
    layerSlider.value = schemData.height - 1;
    state.currentLayerY = null;
    layerValueBadge.textContent = 'Alle Ebenen';

    // Apply Current Transform & Center Camera
    applyPlacementTransform();
    frameCameraOnSchematic(schemData);
}

/**
 * Filter blocks by Y layer (Layer-by-Layer visual slicing)
 */
function updateLayerFilter(maxY) {
    state.currentLayerY = maxY;

    const dummy = new THREE.Object3D();
    const zeroScale = new THREE.Vector3(0, 0, 0);

    const updateMesh = (mesh) => {
        if (!mesh) return;
        const voxels = mesh.userData.voxels;
        for (let i = 0; i < voxels.length; i++) {
            const v = voxels[i];
            if (maxY !== null && v.y > maxY) {
                // Hide voxel by scaling to 0
                dummy.position.set(v.x + 0.5, v.y + 0.5, v.z + 0.5);
                dummy.scale.copy(zeroScale);
                dummy.updateMatrix();
                mesh.setMatrixAt(i, dummy.matrix);
            } else {
                // Show voxel with normal scale 1
                dummy.position.set(v.x + 0.5, v.y + 0.5, v.z + 0.5);
                dummy.scale.set(1, 1, 1);
                dummy.updateMatrix();
                mesh.setMatrixAt(i, dummy.matrix);
            }
        }
        mesh.instanceMatrix.needsUpdate = true;
    };

    updateMesh(solidInstancedMesh);
    updateMesh(transparentInstancedMesh);
}

function applyPlacementTransform() {
    // Minecraft Coordinates Placement:
    schematicGroup.position.set(state.origin.x, state.origin.y, state.origin.z);

    // Rotation in 90-degree steps around Y axis
    const rad = THREE.MathUtils.degToRad(state.rotation);
    schematicGroup.rotation.y = -rad; // Three.js Y-rotation

    // Update origin dummy marker for the Gizmo
    originMarker.position.set(state.origin.x, state.origin.y, state.origin.z);
    originMarker.rotation.y = -rad;

    updateBlueMapTarget();
}

function frameCameraOnSchematic(schemData) {
    const cx = state.origin.x + schemData.width / 2;
    const cy = state.origin.y + schemData.height / 2;
    const cz = state.origin.z + schemData.length / 2;

    orbitControls.target.set(cx, cy, cz);

    const maxDim = Math.max(schemData.width, schemData.height, schemData.length, 12);
    const dist = maxDim * 2.2;
    camera.position.set(cx + dist * 0.7, cy + dist * 0.8, cz + dist * 0.7);
    camera.lookAt(cx, cy, cz);
    orbitControls.update();

    if (gridHelper) {
        gridHelper.position.set(
            Math.round(state.origin.x / 16) * 16,
            state.origin.y - 0.01,
            Math.round(state.origin.z / 16) * 16
        );
    }
}

// ---------------------------------------------------------------------
// UI Updates & Material Summary
// ---------------------------------------------------------------------

function updateUI(schemData) {
    infoCard.style.display = 'block';
    infoFilename.textContent = state.schematicFilename;
    infoDimensions.textContent = `${schemData.width} x ${schemData.height} x ${schemData.length}`;
    infoBlocks.textContent = schemData.activeBlockCount.toLocaleString();

    // Fill Material Table
    materialsTableBody.innerHTML = '';
    totalMaterialsBadge.textContent = `${schemData.materialCounts.length} Typen`;

    for (const item of schemData.materialCounts) {
        const row = document.createElement('tr');
        const hexColor = '#' + getBlockColor(item.block).toString(16).padStart(6, '0');

        let stackText = `${item.count}`;
        if (item.stacks > 0) {
            stackText += ` <span class="stack-badge">(${item.stacks}stk ${item.remainder > 0 ? '+ ' + item.remainder : ''})</span>`;
        }

        row.innerHTML = `
            <td><span class="color-dot" style="background-color: ${hexColor}"></span></td>
            <td><strong>${item.name}</strong><br><small class="text-muted">${item.block}</small></td>
            <td class="text-right">${stackText}</td>
        `;
        materialsTableBody.appendChild(row);
    }
}

function updateOriginInputs() {
    inputOriginX.value = state.origin.x;
    inputOriginY.value = state.origin.y;
    inputOriginZ.value = state.origin.z;
}

// ---------------------------------------------------------------------
// Event Listeners & File Loading
// ---------------------------------------------------------------------

function initEventListeners() {
    // Drag & Drop
    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.add('drag-over');
        });
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.remove('drag-over');
        });
    });

    dropZone.addEventListener('drop', (e) => {
        const files = e.dataTransfer.files;
        if (files && files.length > 0) {
            handleFile(files[0]);
        }
    });

    fileInput.addEventListener('change', (e) => {
        if (e.target.files && e.target.files.length > 0) {
            handleFile(e.target.files[0]);
        }
    });

    // Inputs: ID & Filename
    inputId.addEventListener('input', (e) => {
        state.projectId = e.target.value.trim() || 'bauplan_projekt';
    });

    if (inputTitle) {
        inputTitle.addEventListener('input', (e) => {
            state.title = e.target.value.trim() || state.projectId;
        });
    }

    if (inputOwner) {
        inputOwner.addEventListener('input', (e) => {
            state.owner = e.target.value.trim();
        });
    }

    if (inputStatus) {
        inputStatus.addEventListener('change', (e) => {
            state.status = e.target.value;
        });
    }

    if (inputAssigned) {
        inputAssigned.addEventListener('input', (e) => {
            state.assignedPlayers = e.target.value
                .split(',')
                .map(s => s.trim())
                .filter(s => s.length > 0);
        });
    }

    // Inputs: Origin Coordinates
    [inputOriginX, inputOriginY, inputOriginZ].forEach(input => {
        input.addEventListener('change', () => {
            state.origin.x = parseInt(inputOriginX.value, 10) || 0;
            state.origin.y = parseInt(inputOriginY.value, 10) || 0;
            state.origin.z = parseInt(inputOriginZ.value, 10) || 0;
            applyPlacementTransform();
        });
    });

    // Rotation Selector
    inputRotation.addEventListener('change', (e) => {
        state.rotation = parseInt(e.target.value, 10) || 0;
        applyPlacementTransform();
    });

    // Unlocked Checkbox
    inputUnlocked.addEventListener('change', (e) => {
        state.unlocked = e.target.checked;
    });

    // Layer Slider
    layerSlider.addEventListener('input', (e) => {
        const val = parseInt(e.target.value, 10);
        if (state.schematicData && val >= state.schematicData.height - 1) {
            updateLayerFilter(null);
            layerValueBadge.textContent = 'Alle Ebenen';
        } else {
            updateLayerFilter(val);
            layerValueBadge.textContent = `Y ≤ ${val}`;
        }
    });

    // Reset Camera Button
    btnResetCam.addEventListener('click', () => {
        if (state.schematicData) {
            frameCameraOnSchematic(state.schematicData);
        }
    });

    // Sample Button
    btnSample.addEventListener('click', () => {
        loadSampleData();
    });

    // Test Cube Button
    btnTestCube.addEventListener('click', () => {
        loadTestCubeData();
    });

    // Singleplayer Button
    if (btnSingleplayer) {
        btnSingleplayer.addEventListener('click', () => {
            loadSingleplayerWorldData();
        });
    }

    // Toggle Terrain Button
    if (btnToggleTerrain) {
        btnToggleTerrain.addEventListener('click', () => {
            if (terrainMesh) {
                terrainMesh.visible = !terrainMesh.visible;
                btnToggleTerrain.innerHTML = terrainMesh.visible
                    ? '<span>⛰️</span> Terrain: An'
                    : '<span>⛰️</span> Terrain: Aus';
            }
        });
    }

    // Export Project JSON Button
    btnExport.addEventListener('click', exportProjectJson);

    // Download Schematic Button (if uploaded)
    btnDownloadSchem.addEventListener('click', () => {
        if (state.uploadedFileBlob) {
            downloadBlob(state.uploadedFileBlob, state.schematicFilename);
        } else {
            alert('Lade zuerst eine eigene .schem Datei hoch, um diese herunterzuladen.');
        }
    });

    // Viewport Mode Switchers
    if (tabVoxelView) {
        tabVoxelView.addEventListener('click', () => setViewportMode('voxel'));
    }
    if (tabLivemapView) {
        tabLivemapView.addEventListener('click', () => setViewportMode('livemap'));
    }
    if (tabSplitView) {
        tabSplitView.addEventListener('click', () => setViewportMode('split'));
    }

    // Fly to current project on BlueMap
    if (btnFlyBlueMap) {
        btnFlyBlueMap.addEventListener('click', flyBlueMapToCurrentProject);
    }

    // BlueMap Marker Modal
    if (btnBlueMapModal) {
        btnBlueMapModal.addEventListener('click', openBlueMapModal);
    }
    if (btnModalClose) {
        btnModalClose.addEventListener('click', closeBlueMapModal);
    }
    if (blueMapModal) {
        blueMapModal.addEventListener('click', (e) => {
            if (e.target === blueMapModal) closeBlueMapModal();
        });
    }
    if (tabModalHocon) {
        tabModalHocon.addEventListener('click', () => setModalTab('hocon'));
    }
    if (tabModalJson) {
        tabModalJson.addEventListener('click', () => setModalTab('json'));
    }
    if (btnModalCopy) {
        btnModalCopy.addEventListener('click', copyModalCode);
    }
    if (btnModalDownload) {
        btnModalDownload.addEventListener('click', downloadModalFile);
    }

    // Livemap Protocol & Copy Handling
    const isHttps = window.location.protocol === 'https:';
    if (isHttps) {
        if (mixedContentWarning) mixedContentWarning.style.display = 'flex';
        if (bluemapIframe) bluemapIframe.style.display = 'none';
    } else {
        if (mixedContentWarning) mixedContentWarning.style.display = 'none';
        if (bluemapIframe) {
            bluemapIframe.style.display = 'block';
            bluemapIframe.src = 'http://161.97.151.79:8100/#kekwelt';
        }
    }

    if (btnCopyMapUrl) {
        btnCopyMapUrl.addEventListener('click', () => {
            const x = state.origin.x;
            const y = state.origin.y;
            const z = state.origin.z;
            const targetUrl = `http://161.97.151.79:8100/#kekwelt:${x}:${y}:${z}:75:0:0`;
            navigator.clipboard.writeText(targetUrl).then(() => {
                btnCopyMapUrl.innerHTML = '<span>✔</span> URL kopiert!';
                setTimeout(() => {
                    btnCopyMapUrl.innerHTML = '<span>📋</span> URL kopieren';
                }, 2000);
            }).catch(() => {
                prompt('BlueMap URL:', targetUrl);
            });
        });
    }

    updateBlueMapTarget();
}

async function handleFile(file) {
    if (!file.name.endsWith('.schem') && !file.name.endsWith('.schematic')) {
        alert('Bitte wähle eine gültige Sponge-Schematic Datei (.schem) aus.');
        return;
    }

    try {
        dropZone.classList.add('loading');
        dropZone.querySelector('.drop-title').textContent = 'Lese Schematic...';

        const arrayBuffer = await file.arrayBuffer();
        const schemData = await parseSpongeSchematic(arrayBuffer);

        state.schematicData = schemData;
        state.uploadedFileBlob = new Blob([arrayBuffer]);
        state.schematicFilename = file.name;

        // Auto-generate project ID & title from filename without extension
        const baseName = file.name.replace(/\.[^/.]+$/, "").replace(/[^a-zA-Z0-9_-]/g, "_").toLowerCase();
        state.projectId = baseName;
        state.title = file.name.replace(/\.[^/.]+$/, "").replace(/[_ -]+/g, " ");
        inputId.value = baseName;
        if (inputTitle) inputTitle.value = state.title;

        renderSchematicVoxels(schemData);
        updateUI(schemData);

        dropZone.querySelector('.drop-title').textContent = 'Schematic erfolgreich geladen!';
        setTimeout(() => {
            dropZone.classList.remove('loading');
            dropZone.querySelector('.drop-title').textContent = 'Schematic (.schem) ablegen';
        }, 1500);

    } catch (err) {
        console.error('Fehler beim Laden der Schematic:', err);
        alert('Fehler beim Parsen der Schematic: ' + err.message);
        dropZone.classList.remove('loading');
        dropZone.querySelector('.drop-title').textContent = 'Schematic (.schem) ablegen';
    }
}

function loadSampleData() {
    const sample = createSampleCottage();
    state.schematicData = sample;
    state.schematicFilename = 'beispiel_haus.schem';
    state.projectId = 'beispiel_haus';
    state.title = 'Beispiel Haus';
    inputId.value = state.projectId;
    if (inputTitle) inputTitle.value = state.title;

    renderSchematicVoxels(sample);
    updateUI(sample);
}

function loadTestCubeData() {
    const cube = createTestCube();
    state.schematicData = cube;
    state.schematicFilename = 'test.schem';
    state.projectId = 'test_project';
    state.title = 'Test Würfel';
    state.origin = { x: 0, y: 70, z: 0 };
    state.rotation = 0;
    state.unlocked = true;

    inputId.value = state.projectId;
    if (inputTitle) inputTitle.value = state.title;
    inputUnlocked.checked = true;
    updateOriginInputs();
    inputRotation.value = "0";

    renderSchematicVoxels(cube);
    updateUI(cube);
}

async function loadWorldTerrainMesh() {
    if (terrainMesh) {
        scene.remove(terrainMesh);
        terrainMesh.geometry.dispose();
        terrainMesh.material.dispose();
        terrainMesh = null;
    }

    try {
        const resp = await fetch('singleplayer_world.json');
        if (!resp.ok) return;
        const data = await resp.json();

        const w = data.gridWidth;
        const l = data.gridLength;
        const heights = data.heights;

        // PlaneGeometry: width, height, widthSegments, heightSegments
        const planeGeo = new THREE.PlaneGeometry(w, l, w - 1, l - 1);
        planeGeo.rotateX(-Math.PI / 2); // Lay horizontal (XZ plane)

        const posAttr = planeGeo.attributes.position;
        for (let i = 0; i < posAttr.count; i++) {
            if (i < heights.length) {
                posAttr.setY(i, heights[i]);
            }
        }
        planeGeo.computeVertexNormals();

        // Shaded Minecraft grass/dirt terrain material
        const terrainMat = new THREE.MeshLambertMaterial({
            color: 0x487532, // Minecraft lush grass green
            wireframe: false,
            flatShading: true
        });

        terrainMesh = new THREE.Mesh(planeGeo, terrainMat);
        const centerX = data.minX + w / 2;
        const centerZ = data.minZ + l / 2;
        terrainMesh.position.set(centerX, 0, centerZ);
        scene.add(terrainMesh);

        console.log('[Terrain] 3D Singleplayer Welt erfolgreich geladen!');
    } catch (e) {
        console.warn('[Terrain] Konnte Welt-Terrain nicht laden:', e);
    }
}

async function loadSingleplayerWorldData() {
    try {
        dropZone.querySelector('.drop-title').textContent = 'Lade Singleplayer-Welt...';
        const resp = await fetch('mein_bauwerk.schem');
        if (!resp.ok) throw new Error('mein_bauwerk.schem nicht gefunden');
        const arrayBuffer = await resp.arrayBuffer();
        const schemData = await parseSpongeSchematic(arrayBuffer);

        state.schematicData = schemData;
        state.uploadedFileBlob = new Blob([arrayBuffer]);
        state.schematicFilename = 'mein_bauwerk.schem';
        state.projectId = 'mein_bauwerk';
        state.title = 'Mein Bauwerk (New World)';
        state.origin = { x: 237, y: -60, z: -443 };
        state.rotation = 270;
        state.unlocked = true;

        inputId.value = state.projectId;
        if (inputTitle) inputTitle.value = state.title;
        inputOriginX.value = state.origin.x;
        inputOriginY.value = state.origin.y;
        inputOriginZ.value = state.origin.z;
        inputRotation.value = '270';
        inputUnlocked.checked = true;

        // Render terrain from singleplayer world
        await loadWorldTerrainMesh();

        // Render voxels
        renderSchematicVoxels(schemData);
        updateUI(schemData);
        frameCameraOnSchematic(schemData);

        dropZone.querySelector('.drop-title').textContent = 'Singleplayer-Bauwerk geladen!';
        setTimeout(() => {
            dropZone.querySelector('.drop-title').textContent = 'Schematic (.schem) ablegen';
        }, 1500);
    } catch (e) {
        console.error('Fehler beim Laden der Singleplayer-Welt:', e);
    }
}

// ---------------------------------------------------------------------
// Export JSON
// ---------------------------------------------------------------------

function exportProjectJson() {
    const assigned = (inputAssigned ? inputAssigned.value : '')
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0);

    const exportObject = {
        id: state.projectId,
        title: (inputTitle && inputTitle.value.trim()) ? inputTitle.value.trim() : (state.title || state.projectId),
        schematic: state.schematicFilename,
        owner: (inputOwner && inputOwner.value.trim()) ? inputOwner.value.trim() : (state.owner || ''),
        assignedPlayers: assigned,
        status: (inputStatus ? inputStatus.value : (state.status || 'PLANNED')),
        origin: {
            x: state.origin.x,
            y: state.origin.y,
            z: state.origin.z
        },
        rotation: state.rotation,
        unlocked: state.unlocked
    };

    const jsonString = JSON.stringify(exportObject, null, 2);
    const blob = new Blob([jsonString], { type: 'application/json' });
    const fileName = `${state.projectId}.json`;

    downloadBlob(blob, fileName);
}

function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// ---------------------------------------------------------------------
// BlueMap Livemap Integration & Viewport Mode Handling
// ---------------------------------------------------------------------

function updateBlueMapTarget() {
    const x = state.origin.x;
    const y = state.origin.y;
    const z = state.origin.z;
    const targetUrl = `http://161.97.151.79:8100/#kekwelt:${x}:${y}:${z}:75:0:0`;
    if (btnBannerOpenMap) {
        btnBannerOpenMap.href = targetUrl;
    }
}

function setViewportMode(mode) {
    [tabVoxelView, tabLivemapView, tabSplitView].forEach(tab => {
        if (tab) tab.classList.remove('active');
    });

    const isHttps = window.location.protocol === 'https:';

    if (mode === 'voxel') {
        if (tabVoxelView) tabVoxelView.classList.add('active');
        if (viewportContainer) viewportContainer.classList.remove('split-mode');
        if (canvasContainer) canvasContainer.style.display = 'block';
        if (livemapContainer) livemapContainer.style.display = 'none';
        onWindowResize();
    } else if (mode === 'livemap') {
        if (tabLivemapView) tabLivemapView.classList.add('active');
        if (viewportContainer) viewportContainer.classList.remove('split-mode');
        if (canvasContainer) canvasContainer.style.display = 'none';
        if (livemapContainer) livemapContainer.style.display = 'block';
        if (isHttps) {
            if (mixedContentWarning) mixedContentWarning.style.display = 'flex';
            if (bluemapIframe) bluemapIframe.style.display = 'none';
        } else {
            if (mixedContentWarning) mixedContentWarning.style.display = 'none';
            if (bluemapIframe) {
                bluemapIframe.style.display = 'block';
                if (!bluemapIframe.src || bluemapIframe.src === 'about:blank' || bluemapIframe.src.indexOf('161.97.151.79') === -1) {
                    bluemapIframe.src = `http://161.97.151.79:8100/#kekwelt:${state.origin.x}:${state.origin.y}:${state.origin.z}:75:0:0`;
                }
            }
        }
    } else if (mode === 'split') {
        if (tabSplitView) tabSplitView.classList.add('active');
        if (viewportContainer) viewportContainer.classList.add('split-mode');
        if (canvasContainer) canvasContainer.style.display = 'block';
        if (livemapContainer) livemapContainer.style.display = 'block';
        if (isHttps) {
            if (mixedContentWarning) mixedContentWarning.style.display = 'flex';
            if (bluemapIframe) bluemapIframe.style.display = 'none';
        } else {
            if (mixedContentWarning) mixedContentWarning.style.display = 'none';
            if (bluemapIframe) {
                bluemapIframe.style.display = 'block';
                if (!bluemapIframe.src || bluemapIframe.src === 'about:blank' || bluemapIframe.src.indexOf('161.97.151.79') === -1) {
                    bluemapIframe.src = `http://161.97.151.79:8100/#kekwelt:${state.origin.x}:${state.origin.y}:${state.origin.z}:75:0:0`;
                }
            }
        }
        onWindowResize();
    }
}

function flyBlueMapToCurrentProject() {
    const x = state.origin.x;
    const y = state.origin.y;
    const z = state.origin.z;
    const targetUrl = `http://161.97.151.79:8100/#kekwelt:${x}:${y}:${z}:75:0:0`;
    
    updateBlueMapTarget();

    if (window.location.protocol === 'https:') {
        window.open(targetUrl, '_blank');
    } else {
        if (bluemapIframe) {
            bluemapIframe.src = targetUrl;
        }
        if (viewportContainer && !viewportContainer.classList.contains('split-mode') && livemapContainer.style.display === 'none') {
            setViewportMode('split');
        }
    }
}

// ---------------------------------------------------------------------
// BlueMap Marker Generator Modal
// ---------------------------------------------------------------------

function getProjectMarkerData() {
    const w = state.schematicData ? state.schematicData.width : 10;
    const h = state.schematicData ? state.schematicData.height : 10;
    const l = state.schematicData ? state.schematicData.length : 10;

    const ox = state.origin.x;
    const oy = state.origin.y;
    const oz = state.origin.z;
    const rot = ((state.rotation || 0) % 360 + 360) % 360;

    const effW = (rot === 90 || rot === 270) ? l : w;
    const effL = (rot === 90 || rot === 270) ? w : l;

    const centerX = ox + effW / 2;
    const centerZ = oz + effL / 2;
    const topY = oy + h;

    const status = (state.status || 'PLANNED').toUpperCase();
    let colorName = 'Geplant', badge = '🟡 Geplant', rgb = { r: 234, g: 179, b: 8 }, hex = '#eab308';
    if (status === 'COMPLETED' || status === 'FERTIG') {
        colorName = 'Fertig'; badge = '🟢 Fertig'; rgb = { r: 34, g: 197, b: 94 }; hex = '#22c55e';
    } else if (status === 'IN_PROGRESS' || status === 'BAU') {
        colorName = 'Im Bau'; badge = '🟠 Im Bau'; rgb = { r: 249, g: 115, b: 22 }; hex = '#f97316';
    }

    const title = (inputTitle && inputTitle.value.trim()) ? inputTitle.value.trim() : (state.title || state.projectId);
    const owner = (inputOwner && inputOwner.value.trim()) ? inputOwner.value.trim() : (state.owner || 'Gemeinschaft');
    const assigned = (inputAssigned && inputAssigned.value.trim()) ? inputAssigned.value.trim() : (state.assignedPlayers?.join(', ') || 'Alle (öffentlich)');

    const id = (state.projectId || 'bauplan').replace(/[^a-zA-Z0-9_-]/g, '_').toLowerCase();

    const detailHtml = `<div style="font-family: sans-serif; min-width: 190px; padding: 4px;"><h3 style="margin: 0 0 6px 0; color: #f1f5f9; font-size: 15px; border-bottom: 2px solid ${hex}; padding-bottom: 4px;">${title}</h3><p style="margin: 4px 0; font-size: 13px;"><b>Status:</b> <span style="color:${hex}; font-weight:bold;">${badge}</span></p><p style="margin: 4px 0; font-size: 13px;"><b>Bauleiter:</b> <span style="color:#38bdf8;">${owner}</span></p><p style="margin: 4px 0; font-size: 13px;"><b>Zuständig:</b> <span style="color:#4ade80;">${assigned}</span></p><p style="margin: 4px 0; font-size: 12px; color:#94a3b8;"><b>Position:</b> ${ox}, ${oy}, ${oz} (${rot}°)</p><p style="margin: 4px 0; font-size: 12px; color:#94a3b8;"><b>Maße:</b> ${w} x ${h} x ${l} Blöcke</p></div>`.replace(/"/g, '\\"');

    const hocon = `# In: config/bluemap/maps/kekwelt.conf unter 'marker-sets: { ... }' einfuegen:
marker-sets: {
    schem-planner: {
        label: "📐 Baupläne & Stadtplanung"
        toggleable: true
        default-hidden: false
        sorting: 0

        markers: {
            ${id}_box: {
                type: "extrude"
                label: "${title} [3D Zone]"
                position: { x: ${centerX}, y: ${oy}, z: ${centerZ} }
                shape-y: ${oy}
                shape: [
                    { x: ${ox}, z: ${oz} },
                    { x: ${ox + effW}, z: ${oz} },
                    { x: ${ox + effW}, z: ${oz + effL} },
                    { x: ${ox}, z: ${oz + effL} }
                ]
                height: ${h}
                line-color: { r: ${rgb.r}, g: ${rgb.g}, b: ${rgb.b}, a: 1.0 }
                fill-color: { r: ${rgb.r}, g: ${rgb.g}, b: ${rgb.b}, a: 0.28 }
                line-width: 2
                detail: "${detailHtml}"
            }
            ${id}_pin: {
                type: "poi"
                label: "${title} (${colorName})"
                position: { x: ${centerX}, y: ${topY + 1.5}, z: ${centerZ} }
                min-distance: 5
                max-distance: 25000
                detail: "${detailHtml}"
            }
        }
    }
}`;

    const json = {
        "schem-planner": {
            label: "📐 Baupläne & Stadtplanung",
            toggleable: true,
            defaultHidden: false,
            sorting: 0,
            markers: {
                [`${id}_box`]: {
                    type: "extrude",
                    label: `${title} [3D Zone]`,
                    position: { x: centerX, y: oy, z: centerZ },
                    shapeY: oy,
                    shape: [
                        { x: ox, z: oz },
                        { x: ox + effW, z: oz },
                        { x: ox + effW, z: oz + effL },
                        { x: ox, z: oz + effL }
                    ],
                    height: h,
                    lineColor: { r: rgb.r, g: rgb.g, b: rgb.b, a: 1.0 },
                    fillColor: { r: rgb.r, g: rgb.g, b: rgb.b, a: 0.28 },
                    lineWidth: 2,
                    detail: detailHtml.replace(/\\"/g, '"')
                },
                [`${id}_pin`]: {
                    type: "poi",
                    label: `${title} (${colorName})`,
                    position: { x: centerX, y: topY + 1.5, z: centerZ },
                    minDistance: 5,
                    maxDistance: 25000,
                    detail: detailHtml.replace(/\\"/g, '"')
                }
            }
        }
    };

    return { hocon, jsonString: JSON.stringify(json, null, 2), id };
}

function openBlueMapModal() {
    updateModalCode();
    if (blueMapModal) {
        blueMapModal.classList.add('open');
    }
}

function closeBlueMapModal() {
    if (blueMapModal) {
        blueMapModal.classList.remove('open');
    }
}

function setModalTab(tab) {
    currentModalTab = tab;
    if (tabModalHocon) tabModalHocon.classList.toggle('active', tab === 'hocon');
    if (tabModalJson) tabModalJson.classList.toggle('active', tab === 'json');
    updateModalCode();
}

function updateModalCode() {
    if (!modalCodeContent) return;
    const data = getProjectMarkerData();
    modalCodeContent.textContent = currentModalTab === 'hocon' ? data.hocon : data.jsonString;
}

function copyModalCode() {
    const data = getProjectMarkerData();
    const textToCopy = currentModalTab === 'hocon' ? data.hocon : data.jsonString;
    navigator.clipboard.writeText(textToCopy).then(() => {
        if (btnModalCopy) {
            const originalHtml = btnModalCopy.innerHTML;
            btnModalCopy.innerHTML = '<span>✔</span> Kopiert!';
            setTimeout(() => {
                btnModalCopy.innerHTML = originalHtml;
            }, 1800);
        }
    }).catch(err => {
        console.error('Konnte Code nicht kopieren:', err);
    });
}

function downloadModalFile() {
    const data = getProjectMarkerData();
    if (currentModalTab === 'hocon') {
        const blob = new Blob([data.hocon], { type: 'text/plain' });
        downloadBlob(blob, `kekwelt_${data.id}_markers.conf`);
    } else {
        const blob = new Blob([data.jsonString], { type: 'application/json' });
        downloadBlob(blob, `markers_${data.id}.json`);
    }
}

