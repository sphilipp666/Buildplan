package net.schemplanner.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.placement.PlacementSession;
import net.schemplanner.render.GhostBlockRenderer;
import net.schemplanner.schematic.MaterialRequirement;
import net.schemplanner.selection.AreaSelectionManager;
import net.schemplanner.sync.GitHubProjectSync;
import net.schemplanner.util.RaycastHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.Map;

/**
 * Main GUI Screen for Schematic Management, Live 3D World Placement, and Material Requirement List.
 * Fully overhauled with clean window styling, direct numeric coordinate inputs, and free-walk mode trigger.
 */
public class MaterialListScreen extends Screen {

    public enum ViewMode {
        MATERIALS,
        SCHEMATICS,
        PLACEMENT,
        AREA_SELECTION
    }

    private ViewMode currentMode = ViewMode.MATERIALS;
    private MaterialListWidget materialListWidget;
    private SchematicListWidget schematicListWidget;
    private EditBox searchField;
    private Button filterButton;
    private ProjectManager.LoadedProject placementProject;

    // Coordinate inputs for Live Placement HUD
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private boolean updatingFields = false;

    // Area Selection / Copy fields
    private EditBox schematicNameField;
    private boolean includeAirBlocks = false;
    private Button includeAirButton;
    private String saveFeedbackMessage = null;
    private int saveFeedbackColor = 0xFF55FF55;

    public MaterialListScreen() {
        super(Component.translatable("gui.schemplanner.title"));
    }

    public void setViewMode(ViewMode mode) {
        this.currentMode = mode;
    }

    public void switchMode(ViewMode mode) {
        this.currentMode = mode;
        this.rebuildWidgets();
    }

    public void switchToSchematicsTab() {
        this.currentMode = ViewMode.SCHEMATICS;
        if (this.minecraft != null && this.width > 0) {
            this.rebuildWidgets();
        }
    }

    public void switchToAreaSelectionTab() {
        this.currentMode = ViewMode.AREA_SELECTION;
        if (this.minecraft != null && this.width > 0) {
            this.rebuildWidgets();
        }
    }

    public void openPlacementMode(ProjectManager.LoadedProject project) {
        this.placementProject = project;
        switchMode(ViewMode.PLACEMENT);
    }

    public void refreshAll() {
        if (materialListWidget != null) {
            refreshMaterials();
        }
        if (schematicListWidget != null) {
            schematicListWidget.refreshList();
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        if (this.currentMode == ViewMode.MATERIALS) {
            initMaterialsView();
        } else if (this.currentMode == ViewMode.SCHEMATICS) {
            initSchematicsView();
        } else if (this.currentMode == ViewMode.PLACEMENT) {
            initPlacementView();
        } else if (this.currentMode == ViewMode.AREA_SELECTION) {
            initAreaSelectionView();
        }
    }

    private void addTopTabs(int winLeft, int winTop, int winWidth) {
        int tabWidth = (winWidth - 28) / 3;

        Button tabSchematics = Button.builder(
                currentMode == ViewMode.SCHEMATICS ? Component.literal("§a[Baupläne]") : Component.literal("Baupläne"),
                btn -> switchMode(ViewMode.SCHEMATICS)
        ).bounds(winLeft + 8, winTop + 8, tabWidth, 20).build();
        if (currentMode == ViewMode.SCHEMATICS) tabSchematics.active = false;
        this.addRenderableWidget(tabSchematics);

        Button tabMaterials = Button.builder(
                currentMode == ViewMode.MATERIALS ? Component.literal("§a[Materialliste]") : Component.literal("Materialliste"),
                btn -> switchMode(ViewMode.MATERIALS)
        ).bounds(winLeft + 12 + tabWidth, winTop + 8, tabWidth, 20).build();
        if (currentMode == ViewMode.MATERIALS) tabMaterials.active = false;
        this.addRenderableWidget(tabMaterials);

        Button tabSelection = Button.builder(
                currentMode == ViewMode.AREA_SELECTION ? Component.literal("§a[📐 Kopieren]") : Component.literal("📐 Kopieren"),
                btn -> switchMode(ViewMode.AREA_SELECTION)
        ).bounds(winLeft + 16 + tabWidth * 2, winTop + 8, tabWidth, 20).build();
        if (currentMode == ViewMode.AREA_SELECTION) tabSelection.active = false;
        this.addRenderableWidget(tabSelection);
    }

    private void initMaterialsView() {
        int winWidth = Math.min(this.width - 20, 480);
        int winHeight = Math.min(this.height - 20, 320);
        int winLeft = (this.width - winWidth) / 2;
        int winTop = (this.height - winHeight) / 2;

        addTopTabs(winLeft, winTop, winWidth);

        // Search Field & Filter Button
        this.searchField = new EditBox(this.font, winLeft + 10, winTop + 48, winWidth - 140, 18, Component.translatable("gui.schemplanner.search"));
        this.searchField.setResponder(text -> {
            if (this.materialListWidget != null) {
                this.materialListWidget.setSearchFilter(text);
            }
        });
        this.addRenderableWidget(this.searchField);

        this.filterButton = Button.builder(getFilterButtonText(), btn -> {
            if (this.materialListWidget != null) {
                boolean nextState = !this.materialListWidget.isOnlyMissing();
                this.materialListWidget.setOnlyMissing(nextState);
                this.filterButton.setMessage(getFilterButtonText());
            }
        }).bounds(winLeft + winWidth - 120, winTop + 47, 110, 20).build();
        this.addRenderableWidget(this.filterButton);

        // Scrollable Material List
        int listTop = winTop + 72;
        int listHeight = winHeight - 104;
        this.materialListWidget = new MaterialListWidget(this.minecraft, winWidth, listHeight, listTop, 26);
        refreshMaterials();
        this.addRenderableWidget(this.materialListWidget);

        // Bottom Actions
        int btnY = winTop + winHeight - 26;
        int halfTabWidth = (winWidth - 22) / 2;
        Button reloadBtn = Button.builder(Component.literal("Neu laden"), btn -> {
            ProjectManager.reloadProjects();
            refreshAll();
        }).bounds(winLeft + 10, btnY, halfTabWidth, 20).build();
        this.addRenderableWidget(reloadBtn);

        Button doneBtn = Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
                .bounds(winLeft + 14 + halfTabWidth, btnY, halfTabWidth, 20).build();
        this.addRenderableWidget(doneBtn);
    }

    private void initSchematicsView() {
        int winWidth = Math.min(this.width - 20, 480);
        int winHeight = Math.min(this.height - 20, 320);
        int winLeft = (this.width - winWidth) / 2;
        int winTop = (this.height - winHeight) / 2;

        addTopTabs(winLeft, winTop, winWidth);

        // Team Visibility Filter Button
        Button teamFilterBtn = Button.builder(getTeamFilterButtonText(), btn -> {
            ProjectManager.toggleVisibilityFilter();
            btn.setMessage(getTeamFilterButtonText());
            refreshAll();
        }).bounds(winLeft + 8, winTop + 31, winWidth - 16, 18).build();
        this.addRenderableWidget(teamFilterBtn);

        // Schematic List Widget
        int listTop = winTop + 52;
        int listHeight = winHeight - 84;
        this.schematicListWidget = new SchematicListWidget(this, this.minecraft, winWidth, listHeight, listTop, 36);
        this.addRenderableWidget(this.schematicListWidget);

        // Bottom Actions
        int btnY = winTop + winHeight - 26;
        Button openFolderBtn = Button.builder(Component.literal("Ordner öffnen"), btn -> ProjectManager.openSchematicsFolder())
                .bounds(winLeft + 10, btnY, 110, 20).build();
        this.addRenderableWidget(openFolderBtn);

        Button reloadBtn = Button.builder(Component.literal("Neu laden"), btn -> {
            ProjectManager.reloadProjects();
            refreshAll();
        }).bounds(winLeft + 126, btnY, 80, 20).build();
        this.addRenderableWidget(reloadBtn);

        Button syncBtn = Button.builder(Component.literal("§bGitHub Sync"), btn -> {
            if (this.minecraft != null) {
                GitHubProjectSync.sync(this.minecraft, msg -> {
                    if (this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal(msg));
                    }
                    this.refreshAll();
                });
            }
        }).bounds(winLeft + 212, btnY, 110, 20).build();
        this.addRenderableWidget(syncBtn);

        Button doneBtn = Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
                .bounds(winLeft + winWidth - 110, btnY, 100, 20).build();
        this.addRenderableWidget(doneBtn);
    }

    private void initPlacementView() {
        if (placementProject == null) {
            placementProject = ProjectManager.getActiveProject();
        }

        int panelW = 460;
        int panelH = 124;
        int panelX = (this.width - panelW) / 2;
        int panelY = this.height - panelH - 8;

        // --- ROW 1: X-Achse & Z-Achse side-by-side ---
        int row1Y = panelY + 26;

        // X Controls
        this.addRenderableWidget(Button.builder(Component.literal("-10"), btn -> nudgeProject(-10, 0, 0)).bounds(panelX + 24, row1Y, 28, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("-1"), btn -> nudgeProject(-1, 0, 0)).bounds(panelX + 54, row1Y, 22, 18).build());
        this.xField = new EditBox(this.font, panelX + 78, row1Y, 56, 18, Component.literal("X"));
        this.xField.setResponder(val -> onCoordinateFieldChanged("X", val));
        this.addRenderableWidget(this.xField);
        this.addRenderableWidget(Button.builder(Component.literal("+1"), btn -> nudgeProject(1, 0, 0)).bounds(panelX + 136, row1Y, 22, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+10"), btn -> nudgeProject(10, 0, 0)).bounds(panelX + 160, row1Y, 28, 18).build());

        // Z Controls
        this.addRenderableWidget(Button.builder(Component.literal("-10"), btn -> nudgeProject(0, 0, -10)).bounds(panelX + 254, row1Y, 28, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("-1"), btn -> nudgeProject(0, 0, -1)).bounds(panelX + 284, row1Y, 22, 18).build());
        this.zField = new EditBox(this.font, panelX + 308, row1Y, 56, 18, Component.literal("Z"));
        this.zField.setResponder(val -> onCoordinateFieldChanged("Z", val));
        this.addRenderableWidget(this.zField);
        this.addRenderableWidget(Button.builder(Component.literal("+1"), btn -> nudgeProject(0, 0, 1)).bounds(panelX + 366, row1Y, 22, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+10"), btn -> nudgeProject(0, 0, 10)).bounds(panelX + 390, row1Y, 28, 18).build());

        // --- ROW 2: Y-Achse, Drehung, Vor mich & Frei herumlaufen ---
        int row2Y = panelY + 48;

        // Y Controls
        this.addRenderableWidget(Button.builder(Component.literal("-10"), btn -> nudgeProject(0, -10, 0)).bounds(panelX + 24, row2Y, 28, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("-1"), btn -> nudgeProject(0, -1, 0)).bounds(panelX + 54, row2Y, 22, 18).build());
        this.yField = new EditBox(this.font, panelX + 78, row2Y, 56, 18, Component.literal("Y"));
        this.yField.setResponder(val -> onCoordinateFieldChanged("Y", val));
        this.addRenderableWidget(this.yField);
        this.addRenderableWidget(Button.builder(Component.literal("+1"), btn -> nudgeProject(0, 1, 0)).bounds(panelX + 136, row2Y, 22, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+10"), btn -> nudgeProject(0, 10, 0)).bounds(panelX + 160, row2Y, 28, 18).build());

        // Rotate & Place in front
        this.addRenderableWidget(Button.builder(Component.literal("Drehen +90°"), btn -> rotateProject()).bounds(panelX + 200, row2Y, 82, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Anvisieren"), btn -> placeInFrontOfPlayer()).bounds(panelX + 286, row2Y, 70, 18).build());

        // Free-Walk Mode Button: Closes GUI so the player can walk around!
        this.addRenderableWidget(Button.builder(Component.literal("§eFrei laufen"), btn -> startFreeWalkSession()).bounds(panelX + 360, row2Y, 88, 18).build());

        // --- ROW 3: Layer navigation & Finish ---
        int row3Y = panelY + 70;
        this.addRenderableWidget(Button.builder(Component.literal("Ebene ▼"), btn -> GhostBlockRenderer.layerDown()).bounds(panelX + 10, row3Y, 65, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Ebene ▲"), btn -> GhostBlockRenderer.layerUp()).bounds(panelX + 78, row3Y, 65, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Filter An/Aus"), btn -> GhostBlockRenderer.toggleLayerFilter()).bounds(panelX + 146, row3Y, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Zurück"), btn -> switchMode(ViewMode.SCHEMATICS)).bounds(panelX + 248, row3Y, 90, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("§aSpeichern"), btn -> this.onClose()).bounds(panelX + 342, row3Y, 108, 20).build());

        syncCoordinateFields();
    }

    private void initAreaSelectionView() {
        int winWidth = Math.min(this.width - 20, 480);
        int winHeight = Math.min(this.height - 20, 320);
        int winLeft = (this.width - winWidth) / 2;
        int winTop = (this.height - winHeight) / 2;

        addTopTabs(winLeft, winTop, winWidth);

        // Pos 1 Buttons
        int row1Y = winTop + 54;
        Button p1LookBtn = Button.builder(Component.literal("Blickziel"), btn -> {
            if (this.minecraft != null) {
                AreaSelectionManager.setPos1(RaycastHelper.getTargetedBlockPos(this.minecraft));
                this.rebuildWidgets();
            }
        }).bounds(winLeft + winWidth - 190, row1Y, 88, 20).build();
        this.addRenderableWidget(p1LookBtn);

        Button p1FeetBtn = Button.builder(Component.literal("Spieler-Pos"), btn -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                AreaSelectionManager.setPos1(this.minecraft.player.blockPosition());
                this.rebuildWidgets();
            }
        }).bounds(winLeft + winWidth - 96, row1Y, 86, 20).build();
        this.addRenderableWidget(p1FeetBtn);

        // Pos 2 Buttons
        int row2Y = winTop + 84;
        Button p2LookBtn = Button.builder(Component.literal("Blickziel"), btn -> {
            if (this.minecraft != null) {
                AreaSelectionManager.setPos2(RaycastHelper.getTargetedBlockPos(this.minecraft));
                this.rebuildWidgets();
            }
        }).bounds(winLeft + winWidth - 190, row2Y, 88, 20).build();
        this.addRenderableWidget(p2LookBtn);

        Button p2FeetBtn = Button.builder(Component.literal("Spieler-Pos"), btn -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                AreaSelectionManager.setPos2(this.minecraft.player.blockPosition());
                this.rebuildWidgets();
            }
        }).bounds(winLeft + winWidth - 96, row2Y, 86, 20).build();
        this.addRenderableWidget(p2FeetBtn);

        // Schematic Name Input
        int row3Y = winTop + 140;
        String prevName = (schematicNameField != null && !schematicNameField.getValue().isEmpty()) ? schematicNameField.getValue() : "mein_bauwerk";
        schematicNameField = new EditBox(this.font, winLeft + 105, row3Y, 195, 20, Component.literal("Name"));
        schematicNameField.setValue(prevName);
        this.addRenderableWidget(schematicNameField);

        // Toggle Air button
        this.includeAirButton = Button.builder(Component.literal(includeAirBlocks ? "Luft: Ja" : "Luft: Nein"), btn -> {
            includeAirBlocks = !includeAirBlocks;
            includeAirButton.setMessage(Component.literal(includeAirBlocks ? "Luft: Ja" : "Luft: Nein"));
        }).bounds(winLeft + 308, row3Y, 110, 20).build();
        this.addRenderableWidget(this.includeAirButton);

        // Big Save Button
        int row4Y = winTop + 172;
        Button saveBtn = Button.builder(Component.literal("💾 Als Schematic (.schem) speichern"), btn -> {
            if (!AreaSelectionManager.isComplete()) return;
            try {
                if (this.minecraft != null && this.minecraft.level != null) {
                    String name = schematicNameField.getValue();
                    var res = AreaSelectionManager.saveToSchematic(this.minecraft.level, name, includeAirBlocks);
                    saveFeedbackMessage = "✔ Gespeichert: " + res.filePath().getFileName() + " (" + res.nonAirBlocks() + " Blöcke)";
                    saveFeedbackColor = 0xFF55FF55;
                    if (this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal("§a✔ Schematic '" + res.filePath().getFileName() + "' gespeichert!"));
                    }
                    this.rebuildWidgets();
                }
            } catch (Exception e) {
                saveFeedbackMessage = "Fehler: " + e.getMessage();
                saveFeedbackColor = 0xFFFF5555;
            }
        }).bounds(winLeft + 20, row4Y, winWidth - 40, 24).build();
        saveBtn.active = AreaSelectionManager.isComplete();
        this.addRenderableWidget(saveBtn);

        // Row 5: Action buttons
        int row5Y = winTop + 206;
        Button inWorldBtn = Button.builder(Component.literal("🏃 Ingame markieren (V/B)"), btn -> {
            AreaSelectionManager.setActive(true);
            this.onClose();
        }).bounds(winLeft + 20, row5Y, (winWidth - 48) / 2, 20).build();
        this.addRenderableWidget(inWorldBtn);

        Button clearBtn = Button.builder(Component.literal("Auswahl leeren"), btn -> {
            AreaSelectionManager.clear();
            saveFeedbackMessage = null;
            this.rebuildWidgets();
        }).bounds(winLeft + 28 + (winWidth - 48) / 2, row5Y, (winWidth - 48) / 2, 20).build();
        this.addRenderableWidget(clearBtn);

        // Bottom Actions
        int btnY = winTop + winHeight - 26;
        Button openFolderBtn = Button.builder(Component.literal("Ordner öffnen"), btn -> ProjectManager.openSchematicsFolder())
                .bounds(winLeft + 10, btnY, 120, 20).build();
        this.addRenderableWidget(openFolderBtn);

        Button toSchemBtn = Button.builder(Component.literal("📂 Zu Bauplänen"), btn -> switchMode(ViewMode.SCHEMATICS))
                .bounds(winLeft + 140, btnY, 130, 20).build();
        this.addRenderableWidget(toSchemBtn);

        Button doneBtn = Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
                .bounds(winLeft + winWidth - 100, btnY, 90, 20).build();
        this.addRenderableWidget(doneBtn);
    }

    private void startFreeWalkSession() {
        if (placementProject != null) {
            PlacementSession.start(placementProject);
        }
        this.onClose();
    }

    private void onCoordinateFieldChanged(String axis, String text) {
        if (placementProject == null || updatingFields) return;
        try {
            String clean = text.trim();
            if (clean.isEmpty() || clean.equals("-")) return;
            int val = Integer.parseInt(clean);
            BlockPos current = placementProject.config().origin.toBlockPos();
            BlockPos next;
            if ("X".equals(axis)) {
                next = new BlockPos(val, current.getY(), current.getZ());
            } else if ("Y".equals(axis)) {
                next = new BlockPos(current.getX(), val, current.getZ());
            } else {
                next = new BlockPos(current.getX(), current.getY(), val);
            }
            ProjectManager.updateProjectTransform(placementProject.config().id, next, placementProject.config().rotation);
        } catch (NumberFormatException ignored) {}
    }

    private void nudgeProject(int dx, int dy, int dz) {
        if (placementProject == null) return;
        BlockPos curr = placementProject.config().origin.toBlockPos();
        BlockPos next = curr.offset(dx, dy, dz);
        ProjectManager.updateProjectTransform(placementProject.config().id, next, placementProject.config().rotation);
        syncCoordinateFields();
    }

    private void rotateProject() {
        if (placementProject == null) return;
        BlockPos curr = placementProject.config().origin.toBlockPos();
        int nextRot = (placementProject.config().rotation + 90) % 360;
        ProjectManager.updateProjectTransform(placementProject.config().id, curr, nextRot);
    }

    private void placeInFrontOfPlayer() {
        if (placementProject == null || minecraft == null) return;
        RaycastHelper.PlacementTarget target = RaycastHelper.getPlacementTarget(minecraft);
        ProjectManager.updateProjectTransform(placementProject.config().id, target.pos(), placementProject.config().rotation);
        syncCoordinateFields();
    }

    private void syncCoordinateFields() {
        if (placementProject == null) return;
        updatingFields = true;
        try {
            BlockPos o = placementProject.config().origin.toBlockPos();
            if (xField != null) xField.setValue(String.valueOf(o.getX()));
            if (yField != null) yField.setValue(String.valueOf(o.getY()));
            if (zField != null) zField.setValue(String.valueOf(o.getZ()));
        } finally {
            updatingFields = false;
        }
    }

    private void refreshMaterials() {
        Collection<MaterialRequirement> materials = ProjectManager.getAggregatedMaterials().values();
        this.materialListWidget.setMaterials(materials);
    }

    private Component getFilterButtonText() {
        return this.materialListWidget != null && this.materialListWidget.isOnlyMissing()
                ? Component.translatable("gui.schemplanner.filter_missing")
                : Component.translatable("gui.schemplanner.filter_all");
    }

    private Component getTeamFilterButtonText() {
        return (ProjectManager.getVisibilityFilter() == ProjectManager.VisibilityFilter.ASSIGNED_ONLY)
                ? Component.literal("§eFilter: §6[🎯 Nur meine Bauten] §7(Klick für: Alle)")
                : Component.literal("§eFilter: §b[🌐 Alle Bauten] §7(Klick für: Nur meine)");
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (currentMode == ViewMode.PLACEMENT) {
            // If one of the edit boxes is focused, let it process the key!
            boolean hasFocus = (xField != null && xField.isFocused()) ||
                               (yField != null && yField.isFocused()) ||
                               (zField != null && zField.isFocused());

            if (!hasFocus) {
                int key = event.key();
                int step = event.hasShiftDown() ? 5 : 1;

                if (key == GLFW.GLFW_KEY_LEFT) {
                    nudgeProject(-step, 0, 0);
                    return true;
                } else if (key == GLFW.GLFW_KEY_RIGHT) {
                    nudgeProject(step, 0, 0);
                    return true;
                } else if (key == GLFW.GLFW_KEY_UP) {
                    nudgeProject(0, 0, -step);
                    return true;
                } else if (key == GLFW.GLFW_KEY_DOWN) {
                    nudgeProject(0, 0, step);
                    return true;
                } else if (key == GLFW.GLFW_KEY_PAGE_UP) {
                    nudgeProject(0, step, 0);
                    return true;
                } else if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
                    nudgeProject(0, -step, 0);
                    return true;
                } else if (key == GLFW.GLFW_KEY_R) {
                    rotateProject();
                    return true;
                } else if (key == GLFW.GLFW_KEY_C) {
                    placeInFrontOfPlayer();
                    return true;
                } else if (key == GLFW.GLFW_KEY_L) {
                    GhostBlockRenderer.toggleLayerFilter();
                    return true;
                } else if (event.isEscape() || key == GLFW.GLFW_KEY_ENTER) {
                    switchMode(ViewMode.SCHEMATICS);
                    return true;
                }
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (currentMode == ViewMode.PLACEMENT) {
            // Floating HUD at bottom: World stays 100% visible
            int panelW = 460;
            int panelH = 124;
            int panelX = (this.width - panelW) / 2;
            int panelY = this.height - panelH - 8;

            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0121622);
            graphics.outline(panelX, panelY, panelW, panelH, 0xFF3A4B6B);
            graphics.outline(panelX + 1, panelY + 1, panelW - 2, panelH - 2, 0xFF1E2838);
        } else {
            // Semi-transparent backdrop for world
            graphics.fill(0, 0, this.width, this.height, 0x77000000);

            // Centered crisp modal window panel
            int winWidth = Math.min(this.width - 20, 480);
            int winHeight = Math.min(this.height - 20, 320);
            int winLeft = (this.width - winWidth) / 2;
            int winTop = (this.height - winHeight) / 2;

            graphics.fill(winLeft, winTop, winLeft + winWidth, winTop + winHeight, 0xF2121622);
            graphics.outline(winLeft, winTop, winWidth, winHeight, 0xFF3A4B6B);
            graphics.outline(winLeft + 1, winTop + 1, winWidth - 2, winHeight - 2, 0xFF1E2838);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int winWidth = Math.min(this.width - 20, 480);
        int winHeight = Math.min(this.height - 20, 320);
        int winLeft = (this.width - winWidth) / 2;
        int winTop = (this.height - winHeight) / 2;

        if (currentMode == ViewMode.MATERIALS) {
            renderSummaryHeader(graphics, winTop + 33);
        } else if (currentMode == ViewMode.SCHEMATICS) {
            graphics.centeredText(this.font, Component.literal("§7Klicke [+ Platzieren] für neue Schematics oder [Justieren]"), this.width / 2, winTop + 32, 0xFFAAAAAA);
        } else if (currentMode == ViewMode.PLACEMENT) {
            renderPlacementHUD(graphics);
        } else if (currentMode == ViewMode.AREA_SELECTION) {
            renderAreaSelectionInfo(graphics, winLeft, winTop, winWidth);
        }
    }

    private void renderAreaSelectionInfo(GuiGraphicsExtractor graphics, int winLeft, int winTop, int winWidth) {
        graphics.centeredText(this.font, Component.literal("§eBereich auswählen & als neue .schem-Datei speichern"), this.width / 2, winTop + 33, 0xFFEEEEEE);

        BlockPos p1 = AreaSelectionManager.getPos1();
        BlockPos p2 = AreaSelectionManager.getPos2();

        String p1Text = (p1 != null) ? String.format("§aPos 1: §f%d, %d, %d", p1.getX(), p1.getY(), p1.getZ()) : "§aPos 1: §c[Nicht gesetzt]";
        graphics.text(this.font, Component.literal(p1Text), winLeft + 20, winTop + 59, 0xFFFFFFFF, true);

        String p2Text = (p2 != null) ? String.format("§9Pos 2: §f%d, %d, %d", p2.getX(), p2.getY(), p2.getZ()) : "§9Pos 2: §c[Nicht gesetzt]";
        graphics.text(this.font, Component.literal(p2Text), winLeft + 20, winTop + 89, 0xFFFFFFFF, true);

        // Dimensions box
        if (AreaSelectionManager.isComplete()) {
            int w = AreaSelectionManager.getWidth();
            int h = AreaSelectionManager.getHeight();
            int l = AreaSelectionManager.getLength();
            int vol = AreaSelectionManager.getVolume();
            String dim = String.format("§bGröße: §f%d × %d × %d §8| §eVolumen: §f%d Blöcke", w, h, l, vol);
            graphics.centeredText(this.font, Component.literal(dim), this.width / 2, winTop + 114, 0xFFDDDDDD);
        } else {
            graphics.centeredText(this.font, Component.literal("§8Tipp: Nutze [V] und [B] ingame, um Ecken mit dem Blickziel zu markieren!"), this.width / 2, winTop + 114, 0xFFAAAAAA);
        }

        graphics.text(this.font, Component.literal("§7Dateiname:"), winLeft + 20, winTop + 146, 0xFFDDDDDD, false);

        if (saveFeedbackMessage != null) {
            graphics.centeredText(this.font, Component.literal(saveFeedbackMessage), this.width / 2, winTop + 242, saveFeedbackColor);
        }
    }

    private void renderSummaryHeader(GuiGraphicsExtractor graphics, int y) {
        Inventory inv = (this.minecraft != null && this.minecraft.player != null) ? this.minecraft.player.getInventory() : null;
        Map<Item, MaterialRequirement> map = ProjectManager.getAggregatedMaterials();

        int totalReq = 0;
        int totalMissing = 0;

        for (MaterialRequirement req : map.values()) {
            totalReq += req.getRequiredCount();
            totalMissing += req.getMissingCount(inv);
        }

        int totalInInv = totalReq - totalMissing;

        String summary = String.format("§7Gesamt: §b%d §8| §7Im Inventar: §a%d §8| §7Fehlend: §c%d", totalReq, totalInInv, totalMissing);
        graphics.centeredText(this.font, Component.literal(summary), this.width / 2, y, 0xFFDDDDDD);
    }

    private void renderPlacementHUD(GuiGraphicsExtractor graphics) {
        if (placementProject == null) {
            graphics.centeredText(this.font, Component.literal("§cKein aktives Projekt ausgewählt!"), this.width / 2, this.height - 110, 0xFFFF5555);
            return;
        }

        int panelW = 460;
        int panelH = 124;
        int panelX = (this.width - panelW) / 2;
        int panelY = this.height - panelH - 8;

        // Top info header inside panel
        String title = "§e[JUSTIERUNG] §f" + placementProject.config().id;
        int rot = placementProject.config().rotation;
        String layerStr = GhostBlockRenderer.isLayerFilterActive() ? ("Y=" + GhostBlockRenderer.currentLayerY) : "Alle";
        String subtitle = String.format("§8|  §7Rot: §e%d°  §8|  §7Ebene: §b%s", rot, layerStr);

        graphics.text(this.font, Component.literal(title), panelX + 12, panelY + 9, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.literal(subtitle), panelX + 230, panelY + 9, 0xFFDDDDDD, false);

        // Labels for coordinate rows
        graphics.text(this.font, Component.literal("§bX:"), panelX + 10, panelY + 31, 0xFF00E5FF, true);
        graphics.text(this.font, Component.literal("§bZ:"), panelX + 240, panelY + 31, 0xFF00E5FF, true);
        graphics.text(this.font, Component.literal("§bY:"), panelX + 10, panelY + 53, 0xFF00E5FF, true);

        // Footer hint
        String hints = "§8[Frei laufen]: Herumlaufen & C drücken zum Zielen! | Shift = 5er";
        graphics.centeredText(this.font, Component.literal(hints), this.width / 2, panelY + 105, 0xFF888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
