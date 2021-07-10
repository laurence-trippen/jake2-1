package jake2.qcommon.network.messages.server;

import jake2.qcommon.Defines;
import jake2.qcommon.edict_t;
import jake2.qcommon.entity_state_t;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.sizebuf_t;

/**
 * Common parent for all messages sent from server to client.
 */
public abstract class ServerMessage implements NetworkMessage {
    public ServerMessageType type;

    public ServerMessage(ServerMessageType type) {
        this.type = type;
    }

    /*
     * CL_ParseEntityBits
     *
     * Returns the entity number (index) and the header flags
     */
    protected static DeltaEntityHeader parseDeltaEntityHeader(sizebuf_t buffer) {
        int flags = sizebuf_t.ReadByte(buffer);
        if ((flags & Defines.U_MOREBITS1) != 0) {
            int b = sizebuf_t.ReadByte(buffer);
            flags |= b << 8;
        }
        if ((flags & Defines.U_MOREBITS2) != 0) {
            int b = sizebuf_t.ReadByte(buffer);
            flags |= b << 16;
        }
        if ((flags & Defines.U_MOREBITS3) != 0) {
            int b = sizebuf_t.ReadByte(buffer);
            flags |= b << 24;
        }

        int number;
        if ((flags & Defines.U_NUMBER16) != 0)
            number = sizebuf_t.ReadShort(buffer);
        else
            number = sizebuf_t.ReadByte(buffer);

        return new DeltaEntityHeader(flags, number);
    }

    /*
     * former CL_ParseDelta
     */
    protected static entity_state_t parseEntityState(int number, int flags, sizebuf_t buffer) {
        entity_state_t to = new entity_state_t(new edict_t(number));
        to.number = number;

        if ((flags & Defines.U_MODEL) != 0)
            to.modelindex = sizebuf_t.ReadByte(buffer);
        if ((flags & Defines.U_MODEL2) != 0)
            to.modelindex2 = sizebuf_t.ReadByte(buffer);
        if ((flags & Defines.U_MODEL3) != 0)
            to.modelindex3 = sizebuf_t.ReadByte(buffer);
        if ((flags & Defines.U_MODEL4) != 0)
            to.modelindex4 = sizebuf_t.ReadByte(buffer);

        if ((flags & Defines.U_FRAME8) != 0)
            to.frame = sizebuf_t.ReadByte(buffer);
        if ((flags & Defines.U_FRAME16) != 0)
            to.frame = sizebuf_t.ReadShort(buffer);

        // used for laser colors
        if ((flags & Defines.U_SKIN8) != 0 && (flags & Defines.U_SKIN16) != 0)
            to.skinnum = sizebuf_t.ReadInt(buffer);
        else if ((flags & Defines.U_SKIN8) != 0)
            to.skinnum = sizebuf_t.ReadByte(buffer);
        else if ((flags & Defines.U_SKIN16) != 0)
            to.skinnum = sizebuf_t.ReadShort(buffer);

        if ((flags & (Defines.U_EFFECTS8 | Defines.U_EFFECTS16)) == (Defines.U_EFFECTS8 | Defines.U_EFFECTS16))
            to.effects = sizebuf_t.ReadInt(buffer);
        else if ((flags & Defines.U_EFFECTS8) != 0)
            to.effects = sizebuf_t.ReadByte(buffer);
        else if ((flags & Defines.U_EFFECTS16) != 0)
            to.effects = sizebuf_t.ReadShort(buffer);

        if ((flags & (Defines.U_RENDERFX8 | Defines.U_RENDERFX16)) == (Defines.U_RENDERFX8 | Defines.U_RENDERFX16))
            to.renderfx = sizebuf_t.ReadInt(buffer);
        else if ((flags & Defines.U_RENDERFX8) != 0)
            to.renderfx = sizebuf_t.ReadByte(buffer);
        else if ((flags & Defines.U_RENDERFX16) != 0)
            to.renderfx = sizebuf_t.ReadShort(buffer);

        if ((flags & Defines.U_ORIGIN1) != 0)
            to.origin[0] = sizebuf_t.ReadCoord(buffer);
        if ((flags & Defines.U_ORIGIN2) != 0)
            to.origin[1] = sizebuf_t.ReadCoord(buffer);
        if ((flags & Defines.U_ORIGIN3) != 0)
            to.origin[2] = sizebuf_t.ReadCoord(buffer);

        if ((flags & Defines.U_ANGLE1) != 0)
            to.angles[0] = sizebuf_t.ReadAngleByte(buffer);
        if ((flags & Defines.U_ANGLE2) != 0)
            to.angles[1] = sizebuf_t.ReadAngleByte(buffer);
        if ((flags & Defines.U_ANGLE3) != 0)
            to.angles[2] = sizebuf_t.ReadAngleByte(buffer);

        if ((flags & Defines.U_OLDORIGIN) != 0)
            sizebuf_t.ReadPos(buffer, to.old_origin);

        if ((flags & Defines.U_SOUND) != 0)
            to.sound = sizebuf_t.ReadByte(buffer);

        if ((flags & Defines.U_EVENT) != 0)
            to.event = sizebuf_t.ReadByte(buffer);
        else
            to.event = 0;

        if ((flags & Defines.U_SOLID) != 0)
            to.solid = sizebuf_t.ReadShort(buffer);
        return to;
    }

    public final void writeTo(sizebuf_t buffer) {
        sizebuf_t.WriteByte(buffer, (byte) type.type);
        writeProperties(buffer);
    }

    protected abstract void writeProperties(sizebuf_t buffer);

    public static ServerMessage parseFromBuffer(sizebuf_t buffer) {

        final int cmd = sizebuf_t.ReadByte(buffer);
        if (cmd == -1)
            return new EndOfServerPacketMessage();
        ServerMessageType type = ServerMessageType.fromInt(cmd);
        final ServerMessage msg;
        // skip parsing of messages not yet migrated to the ServerMessage class
        switch (type) {
            case svc_bad:
            case svc_nop:
            default:
                msg = null;
                break;
            case svc_muzzleflash:
                msg = new WeaponSoundMessage();
                break;
            case svc_muzzleflash2:
                msg = new MuzzleFlash2Message();
                break;
            case svc_layout:
                msg = new LayoutMessage();
                break;
            case svc_inventory:
                msg = new InventoryMessage();
                break;
            case svc_disconnect:
                msg = new DisconnectMessage();
                break;
            case svc_reconnect:
                msg = new ReconnectMessage();
                break;
            case svc_sound:
                msg = new SoundMessage();
                break;
            case svc_print:
                msg = new PrintMessage();
                break;
            case svc_stufftext:
                msg = new StuffTextMessage();
                break;
            case svc_serverdata:
                msg = new ServerDataMessage();
                break;
            case svc_configstring:
                msg = new ConfigStringMessage();
                break;
            case svc_centerprint:
                msg = new PrintCenterMessage();
                break;
            case svc_frame:
                msg = new FrameHeaderMessage();
                break;
            case svc_playerinfo:
                msg = new PlayerInfoMessage();
                break;
            case svc_spawnbaseline:
                msg = new SpawnBaselineMessage();
                break;
            case svc_packetentities:
                msg = new PacketEntitiesMessage();
                break;
            case svc_download:
                msg = new DownloadMessage();
                break;
            case svc_temp_entity:
                int subtype = sizebuf_t.ReadByte(buffer);
                if (PointTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new PointTEMessage(subtype);
                } else if (BeamTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new BeamTEMessage(subtype);
                } else if (BeamOffsetTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new BeamOffsetTEMessage(subtype);
                } else if (PointDirectionTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new PointDirectionTEMessage(subtype);
                } else if (TrailTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new TrailTEMessage(subtype);
                } else if (SplashTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new SplashTEMessage(subtype);
                } else {
                    throw new IllegalStateException("Unexpected temp entity type:" + subtype);
                }
        }

        if (msg != null) {
            msg.parse(buffer);
        }
        return msg;
    }
}
