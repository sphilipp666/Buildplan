package net.schemplanner.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.schematic.MaterialRequirement;

import java.util.Collection;
import java.util.Map;

/**
 * Main GUI Screen for the Material Requirement To-Do List.
 * Updated for Fabric 26.2.
 */
public class MaterialListScreen extends Screen {
    private MaterialListWidget listWidget;
    private TextFieldWidget searchField;
    private ButtonWidget filterButton;

    public MaterialListScreen() {
        super(Text.translatable("gui.schemplanner.title"));
    }

    @Override
    protected void init() {
        super.init();

        int listTop = 56;
        int listHeight = this.height - listTop - 42;

        // 1. Scrollable Material List (Fabric 26.2 constructor: client, width, height, y, itemHeight)
        this.listWidget = new MaterialListWidget(this.client, this.width, listHeight, listTop, 26);
        refreshData();
        this.addSelectableChild(this.listWidget);

        // 2. Search Field
        this.searchField = new TextFieldWidget(this.textRenderer, this.width / 2 - 150, 32, 180, 18, Text.translatable("gui.schemplanner.search"));
        this.searchField.setChangedListener(text -> this.listWidget.setSearchFilter(text));
        this.addSelectableChild(this.searchField);

        // 3. Filter Button ("Alle Blöcke" / "Nur Fehlende")
        this.filterButton = ButtonWidget.builder(getFilterButtonText(), btn -> {
            boolean nextState = !this.listWidget.isOnlyMissing();
            this.listWidget.setOnlyMissing(nextState);
            this.filterButton.setMessage(getFilterButtonText());
        }).dimensions(this.width / 2 + 36, 31, 114, 20).build();
        this.addDrawableChild(this.filterButton);

        // 4. Reload Button
        ButtonWidget reloadBtn = ButtonWidget.builder(Text.literal("Neu laden"), btn -> {
            ProjectManager.reloadProjects();
            refreshData();
        }).dimensions(this.width / 2 - 150, this.height - 30, 140, 20).build();
        this.addDrawableChild(reloadBtn);

        // 5. Done / Close Button
        ButtonWidget doneBtn = ButtonWidget.builder(ScreenTexts.DONE, btn -> this.close())
                .dimensions(this.width / 2 + 10, this.height - 30, 140, 20).build();
        this.addDrawableChild(doneBtn);
    }

    private void refreshData() {
        Collection<MaterialRequirement> materials = ProjectManager.getAggregatedMaterials().values();
        this.listWidget.setMaterials(materials);
    }

    private Text getFilterButtonText() {
        return this.listWidget != null && this.listWidget.isOnlyMissing()
                ? Text.translatable("gui.schemplanner.filter_missing")
                : Text.translatable("gui.schemplanner.filter_all");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Modern 26.2 renderBackground signature
        this.renderBackground(context, mouseX, mouseY, delta);

        // Render List
        this.listWidget.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        // Summary Stats
        renderSummaryHeader(context);

        // Render Search Input
        this.searchField.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSummaryHeader(DrawContext context) {
        PlayerInventory inv = (this.client != null && this.client.player != null) ? this.client.player.getInventory() : null;
        Map<net.minecraft.item.Item, MaterialRequirement> map = ProjectManager.getAggregatedMaterials();

        int totalReq = 0;
        int totalMissing = 0;

        for (MaterialRequirement req : map.values()) {
            totalReq += req.getRequiredCount();
            totalMissing += req.getMissingCount(inv);
        }

        int totalInInv = totalReq - totalMissing;

        String summary = String.format("§7Gesamt: §b%d §8| §7Im Inventar: §a%d §8| §7Fehlend: §c%d", totalReq, totalInInv, totalMissing);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(summary), this.width / 2, 21, 0xDDDDDD);
    }

    @Override
    public boolean shouldPause() {
        return false; // Does not pause game in multiplayer
    }
}
