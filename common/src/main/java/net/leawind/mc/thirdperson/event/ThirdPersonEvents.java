package net.leawind.mc.thirdperson.event;


import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.Window;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientRawInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.leawind.mc.thirdperson.ThirdPerson;
import net.leawind.mc.thirdperson.api.cameraoffset.CameraOffsetMode;
import net.leawind.mc.thirdperson.api.cameraoffset.CameraOffsetScheme;
import net.leawind.mc.thirdperson.impl.config.Config;
import net.leawind.mc.thirdperson.impl.core.rotation.RotateTarget;
import net.leawind.mc.util.math.LMath;
import net.leawind.mc.util.math.vector.api.Vector2d;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.NotNull;

public interface ThirdPersonEvents {
	static void register () {
		ClientTickEvent.CLIENT_PRE.register(ThirdPersonEvents::onClientTickPre);
		ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register(ThirdPersonEvents::onClientPlayerRespawn);
		ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(ThirdPersonEvents::onClientPlayerJoin);
		ClientRawInputEvent.MOUSE_SCROLLED.register(ThirdPersonEvents::onMouseScrolled);
	}

	private static void onClientTickPre (@NotNull Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		if (!ThirdPerson.isAvailable()) {
			return;
		}
		ThirdPerson.ENTITY_AGENT.onClientTickPre();
		ThirdPerson.CAMERA_AGENT.onClientTickPre();
	}

	/**
	 * 当玩家死亡后重生或加入新的维度时触发
	 */
	private static void onClientPlayerRespawn (@NotNull LocalPlayer oldPlayer, @NotNull LocalPlayer newPlayer) {
		onPlayerReset();
		ThirdPerson.LOGGER.info("on Client player respawn");
	}

	private static void onClientPlayerJoin (@NotNull LocalPlayer player) {
		onPlayerReset();
		ThirdPerson.LOGGER.info("on Client player join");
	}

	/**
	 * 使用滚轮调整距离
	 *
	 * @param minecraft mc
	 * @param amount    向前滚是+1，向后滚是-1
	 */
	private static @NotNull EventResult onMouseScrolled (@NotNull Minecraft minecraft, double amount) {
		Config config = ThirdPerson.getConfig();
		if (ThirdPerson.isAdjustingCameraDistance()) {
			double dist = config.cameraOffsetScheme.getMode().getMaxDistance();
			dist = config.distanceMonoList.offset(dist, (int)-Math.signum(amount));
			config.cameraOffsetScheme.getMode().setMaxDistance(dist);
			return EventResult.interruptFalse();
		} else {
			return EventResult.pass();
		}
	}

	private static void onPlayerReset () {
		ThirdPerson.ENTITY_AGENT.reset();
		ThirdPerson.CAMERA_AGENT.reset();
	}

	/**
	 * 调用Camera.setup时触发
	 * <p>
	 * 该调用位于真正渲染画面之前。
	 * <p>
	 * GameRender#render -> GameRender#renderLevel -> Camera#setup
	 */
	static void onCameraSetup (@NotNull BlockGetter level, float partialTick) {
		ThirdPerson.lastPartialTick = partialTick;
		ThirdPerson.CAMERA_AGENT.setLevel(level);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		double now    = Blaze3D.getTime();
		double period = now - ThirdPerson.lastCameraSetupTimeStamp;
		ThirdPerson.lastCameraSetupTimeStamp = now;
		if (ThirdPerson.isThirdPerson()) {
			ThirdPerson.CAMERA_AGENT.onCameraSetup(period);
		}
		if (mc.options.getCameraType().isMirrored()) {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		}
	}

	static void onPreRender (float partialTick) {
		double now    = Blaze3D.getTime();
		double period = now - ThirdPerson.lastRenderTickTimeStamp;
		ThirdPerson.lastRenderTickTimeStamp = now;
		if (ThirdPerson.isThirdPerson() && ThirdPerson.isAvailable() && ThirdPerson.ENTITY_AGENT.isCameraEntityExist()) {
			ThirdPerson.ENTITY_AGENT.onPreRender(period, partialTick);
			ThirdPerson.CAMERA_AGENT.onPreRender(period, partialTick);
		}
	}

	static void onStartAdjustingCameraOffset () {
	}

	static void onStopAdjustingCameraOffset () {
		ThirdPerson.CONFIG_MANAGER.trySave();
	}

	/**
	 * 移动鼠标调整相机偏移
	 *
	 * @param movement 移动的像素
	 */
	static void onAdjustingCameraOffset (@NotNull Vector2d movement) {
		if (movement.lengthSquared() == 0) {
			return;
		}
		Config             config     = ThirdPerson.getConfig();
		Window             window     = Minecraft.getInstance().getWindow();
		Vector2d           screenSize = Vector2d.of(window.getScreenWidth(), window.getScreenHeight());
		CameraOffsetScheme scheme     = config.cameraOffsetScheme;
		CameraOffsetMode   mode       = scheme.getMode();
		if (mode.isCentered()) {
			// 相机在头顶，只能上下调整
			double topOffset = mode.getCenterOffsetRatio();
			topOffset += -movement.y() / screenSize.y();
			topOffset = LMath.clamp(topOffset, -1, 1);
			mode.setCenterOffsetRatio(topOffset);
		} else {
			// 相机没固定在头顶，可以上下左右调整
			Vector2d offset = mode.getSideOffsetRatio(Vector2d.of());
			offset.sub(movement.div(screenSize));
			offset.clamp(-1, 1);
			scheme.setSide(Math.signum(offset.x()));
			mode.setSideOffsetRatio(offset);
		}
	}

	/**
	 * 当玩家与环境交互时，趁交互事件处理前，让玩家看向相机落点
	 */
	static void onBeforeHandleKeybinds () {
		if (ThirdPerson.ENTITY_AGENT.wasInterecting()) {
			// 该方法中使用了mixin，修改了 viewVector
			Minecraft.getInstance().gameRenderer.pick(1.0f);
		}
	}

	/**
	 * 退出第三人称视角
	 */
	static void onLeaveThirdPerson () {
		if (ThirdPerson.getConfig().turn_with_camera_when_enter_first_person) {
			ThirdPerson.ENTITY_AGENT.setRotateTarget(RotateTarget.CAMERA_ROTATION);
		}
	}

	/**
	 * 进入第三人称视角时触发
	 */
	static void onEnterThirdPerson () {
		ThirdPerson.CAMERA_AGENT.reset();
		ThirdPerson.ENTITY_AGENT.reset();
		ThirdPerson.lastCameraSetupTimeStamp = Blaze3D.getTime();
	}
}
