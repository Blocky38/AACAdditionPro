package de.photon.anticheataddition.modules.checks.packetanalysis;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import de.photon.anticheataddition.ServerVersion;
import de.photon.anticheataddition.modules.ModuleLoader;
import de.photon.anticheataddition.modules.ViolationModule;
import de.photon.anticheataddition.protocol.PacketAdapterBuilder;
import de.photon.anticheataddition.protocol.packetwrappers.sentbyclient.IWrapperPlayClientLook;
import de.photon.anticheataddition.user.User;
import de.photon.anticheataddition.user.data.DataKey;
import de.photon.anticheataddition.user.data.TimestampKey;
import de.photon.anticheataddition.util.minecraft.world.MaterialUtil;
import de.photon.anticheataddition.util.violationlevels.Flag;
import de.photon.anticheataddition.util.violationlevels.ViolationLevelManagement;
import de.photon.anticheataddition.util.violationlevels.ViolationManagement;
import lombok.val;
import org.bukkit.block.BlockFace;

import java.util.concurrent.TimeUnit;

public class PacketAnalysisEqualRotation extends ViolationModule
{
    public PacketAnalysisEqualRotation()
    {
        super("PacketAnalysis.parts.EqualRotation");
    }

    @Override
    protected ModuleLoader createModuleLoader()
    {
        val packetAdapter = PacketAdapterBuilder
                .of(PacketType.Play.Client.POSITION_LOOK, PacketType.Play.Client.LOOK)
                .priority(ListenerPriority.LOW)
                .onReceiving(event -> {
                    val user = User.safeGetUserFromPacketEvent(event);
                    if (User.isUserInvalid(user, this)) return;

                    // Get the packet.
                    final IWrapperPlayClientLook lookWrapper = event::getPacket;

                    val currentYaw = lookWrapper.getYaw();
                    val currentPitch = lookWrapper.getPitch();

                    // Boat false positive (usually worse cheats in vehicles as well)
                    if (!user.getPlayer().isInsideVehicle() &&
                        // Not recently teleported
                        !user.hasTeleportedRecently(5000) &&
                        // Same rotation values
                        // LookPacketData automatically updates its values.
                        currentYaw == user.getDataMap().getFloat(DataKey.Float.LAST_PACKET_YAW) &&
                        currentPitch == user.getDataMap().getFloat(DataKey.Float.LAST_PACKET_PITCH) &&
                        // 1.17 client false positive when throwing exp bottles.
                        user.getTimestampMap().at(TimestampKey.LAST_EXPERIENCE_BOTTLE_THROWN).notRecentlyUpdated(5000) &&
                        // LabyMod fp when standing still / hit in corner fp
                        user.hasMovedRecently(TimestampKey.LAST_XZ_MOVEMENT, 100) &&
                        // 1.17 false positives
                        !(user.getTimestampMap().at(TimestampKey.LAST_HOTBAR_SWITCH).recentlyUpdated(3000) && user.hasSneakedRecently(3000)) &&
                        PacketAdapterBuilder.checkSync(10, TimeUnit.SECONDS,
                                                       // False positive when jumping from great heights into a pool with slime blocks / beds on the bottom.
                                                       () -> !(user.isInLiquids() && MaterialUtil.BOUNCE_MATERIALS.contains(user.getPlayer().getLocation().getBlock().getRelative(BlockFace.DOWN).getType())) &&
                                                             // Fixes false positives on versions 1.9+ because of changed hitboxes
                                                             !(ServerVersion.is18() &&
                                                               ServerVersion.getClientServerVersion(user.getPlayer()) != ServerVersion.MC18 &&
                                                               MaterialUtil.containsMaterials(user.getHitbox().getPartiallyIncludedMaterials(user.getPlayer().getLocation()), MaterialUtil.CHANGED_HITBOX_MATERIALS))))
                    {
                        // Cancelled packets may cause problems.
                        if (user.getDataMap().getBoolean(DataKey.Bool.PACKET_ANALYSIS_EQUAL_ROTATION_EXPECTED)) {
                            user.getDataMap().setBoolean(DataKey.Bool.PACKET_ANALYSIS_EQUAL_ROTATION_EXPECTED, false);
                            return;
                        }

                        getManagement().flag(Flag.of(user).setDebug("PacketAnalysisData-Debug | Player: " + user.getPlayer().getName() + " sent equal rotations."));
                    }
                }).build();

        return ModuleLoader.builder(this)
                           .addPacketListeners(packetAdapter)
                           .build();
    }

    @Override
    protected ViolationManagement createViolationManagement()
    {
        return ViolationLevelManagement.builder(this)
                                       .emptyThresholdManagement()
                                       .withDecay(200, 3).build();
    }
}