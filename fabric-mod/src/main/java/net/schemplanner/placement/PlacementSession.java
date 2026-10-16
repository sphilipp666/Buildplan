package net.schemplanner.placement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.util.RaycastHelper;

/**
 * Manages Free-Walk Ingame Placement mode.
 * Allows the player to walk, jump, fly, and look around 360 degrees while positioning the hologram.
 */
public class PlacementSession {
    private static ProjectManager.LoadedProject activeProject;
    private static boolean active = false;

    public static void start(ProjectManager.LoadedProject project) {
        activeProject = project;
        active = true;
    }

    public static void stop() {
        active = false;
        activeProject = null;
    }

    public static boolean isActive() {
        return active && activeProject != null;
    }

    public static ProjectManager.LoadedProject getActiveProject() {
        return activeProject;
    }

    public static void rotate() {
        if (!isActive()) return;
        int nextRot = (activeProject.config().rotation + 90) % 360;
        ProjectManager.updateProjectTransform(activeProject.config().id, activeProject.config().origin.toBlockPos(), nextRot);
    }

    public static void nudge(int dx, int dy, int dz) {
        if (!isActive()) return;
        BlockPos curr = activeProject.config().origin.toBlockPos();
        BlockPos next = curr.offset(dx, dy, dz);
        ProjectManager.updateProjectTransform(activeProject.config().id, next, activeProject.config().rotation);
    }

    public static void snapToCrosshair(Minecraft client) {
        if (!isActive()) return;
        RaycastHelper.PlacementTarget target = RaycastHelper.getPlacementTarget(client);
        ProjectManager.updateProjectTransform(activeProject.config().id, target.pos(), activeProject.config().rotation);
    }
}
