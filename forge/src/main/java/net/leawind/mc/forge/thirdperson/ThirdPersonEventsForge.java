package net.leawind.mc.forge.thirdperson;


import net.leawind.mc.api.base.GameEvents;
import net.leawind.mc.api.client.event.ThirdPersonCameraSetupEvent;
import net.leawind.mc.mixin.CameraInvoker;
import net.minecraft.client.Camera;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ThirdPersonEventsForge {
	/**
	 * 低优先级意味着在其他模组之后执行，可以覆盖其他模组对相机位置、朝向所做的修改。
	 */
	@SuppressWarnings("unused")
	@SubscribeEvent(priority=EventPriority.LOW)
	public static void cameraSetupEvent (ViewportEvent.ComputeCameraAngles event) {
		if (GameEvents.thirdPersonCameraSetup != null) {
			Camera                      camera = event.getCamera();
			ThirdPersonCameraSetupEvent evt    = new ThirdPersonCameraSetupEvent((float)event.getPartialTick());
			GameEvents.thirdPersonCameraSetup.accept(evt);
			if (evt.set()) {
				((CameraInvoker)camera).invokeSetPosition(evt.pos);
				event.setYaw(evt.yRot);
				event.setPitch(evt.xRot);
				// Forge does not update rotation, forwards, up, left
				// So need to invoke vanilla `setRotation` here
				((CameraInvoker)camera).invokeSetRotation(evt.yRot, evt.xRot);
			}
		}
	}
}
