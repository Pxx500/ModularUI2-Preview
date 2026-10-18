package example;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.Unpooled;
import dev.modularui.preview.PreviewEntrypoint;

/** Representative client snapshots feed the production builder. There is no local game server. */
public class TransportPlatformPreview implements PreviewEntrypoint {
    protected Kind kind() { return Kind.LANDING_PAD; }
    protected String state() { return "active"; }
    @Override public String owner() { return "galaxia"; }
    @Override public Class<?> previewedClass() { return TileEntityTransportPlatform.class; }

    @Override public Object createPanel(Context context) {
        var tile = new TileEntityTransportPlatform(kind());
        var binding = new NBTTagCompound();
        var endpoint = new NBTTagCompound();
        endpoint.setBoolean("standalone", kind() == Kind.LANDING_PAD);
        endpoint.setString("asset", UUID.fromString("70000000-0000-0000-0000-000000000001").toString());
        binding.setTag("endpointBinding", endpoint);
        tile.endpointBinding().readFromNBT(binding);
        var sync = (PanelSyncManager) context.panelSyncManager();
        var panel = tile.buildUI(null, sync, null);
        ((BooleanSyncValue) sync.findSyncHandlerNullable("platformFormed", 0)).setBoolValue(!state().equals("unformed"), false, false);
        if (kind() == Kind.LANDING_PAD) {
            var snapshot = new NBTTagCompound();
            snapshot.setString("siloName", state().equals("idle") ? "" : "Workshop Silo");
            if (!state().equals("idle")) {
                snapshot.setString("vehicle", "71000000-0000-0000-0000-000000000001");
                snapshot.setString("service", "72000000-0000-0000-0000-000000000001");
                snapshot.setString("silo", "73000000-0000-0000-0000-000000000001");
            }
            if (state().equals("applied")) snapshot.setString("result", "galaxia.transport.platform.rebind_applied");
            if (state().equals("rejected")) snapshot.setString("result", "galaxia.transport.platform.rebind_rejected");
            var packet = new PacketBuffer(Unpooled.buffer());
            try {
                ByteBufUtils.writeTag(packet, snapshot);
                sync.findSyncHandlerNullable("platformService", 0).readOnClient(100, packet);
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            } finally { packet.release(); }
        }
        return panel;
    }

    public static final class Dock extends TransportPlatformPreview { @Override protected Kind kind() { return Kind.DOCK; } }
    public static final class Idle extends TransportPlatformPreview { @Override protected String state() { return "idle"; } }
    public static final class Unformed extends TransportPlatformPreview { @Override protected String state() { return "unformed"; } }
    public static final class Applied extends TransportPlatformPreview { @Override protected String state() { return "applied"; } }
    public static final class Rejected extends TransportPlatformPreview { @Override protected String state() { return "rejected"; } }
}
