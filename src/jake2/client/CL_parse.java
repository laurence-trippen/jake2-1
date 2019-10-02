/*
 * CL_parse.java
 * Copyright (C) 2004
 */
/*
 Copyright (C) 1997-2001 Id Software, Inc.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */
package jake2.client;

import jake2.client.render.model_t;
import jake2.client.sound.S;
import jake2.qcommon.*;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Lib;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * CL_parse
 */
public class CL_parse {

    //// cl_parse.c -- parse a message received from the server

    public static String svc_strings[] = { "svc_bad", "svc_muzzleflash",
            "svc_muzzlflash2", "svc_temp_entity", "svc_layout",
            "svc_inventory", "svc_nop", "svc_disconnect", "svc_reconnect",
            "svc_sound", "svc_print", "svc_stufftext", "svc_serverdata",
            "svc_configstring", "svc_spawnbaseline", "svc_centerprint",
            "svc_download", "svc_playerinfo", "svc_packetentities",
            "svc_deltapacketentities", "svc_frame" };

    //	  =============================================================================

    public static String DownloadFileName(String fn) {
        return FS.getWriteDir() + "/" + fn;
    }

    /**
     * CL_CheckOrDownloadFile returns true if the file exists, 
     * otherwise it attempts to start a
     * download from the server.
     */
    public static boolean CheckOrDownloadFile(String filename) {
        if (filename.indexOf("..") != -1) {
            Com.Printf("Refusing to download a path with ..\n");
            return true;
        }

        if (FS.FileExists(filename)) {
            // it exists, no need to download
            return true;
        }

        ClientGlobals.cls.downloadname = filename;

        // download to a temp name, and only rename
        // to the real name when done, so if interrupted
        // a runt file wont be left
        ClientGlobals.cls.downloadtempname = Com
                .StripExtension(ClientGlobals.cls.downloadname);
        ClientGlobals.cls.downloadtempname += ".tmp";

        //	  ZOID
        // check to see if we already have a tmp for this file, if so, try to
        // resume
        // open the file if not opened yet
        String name = DownloadFileName(ClientGlobals.cls.downloadtempname);

        RandomAccessFile fp = Lib.fopen(name, "r+b");
        
        if (fp != null) { 
            
            // it exists
            long len = 0;

            try {
                len = fp.length();
            } 
            catch (IOException e) {
            }
            

            ClientGlobals.cls.download = fp;

            // give the server an offset to start the download
            Com.Printf("Resuming " + ClientGlobals.cls.downloadname + "\n");
            MSG.WriteByte(ClientGlobals.cls.netchan.message, ClientCommands.CLC_STRINGCMD.value);
            MSG.WriteString(ClientGlobals.cls.netchan.message, "download "
                    + ClientGlobals.cls.downloadname + " " + len);
        } else {
            Com.Printf("Downloading " + ClientGlobals.cls.downloadname + "\n");
            MSG.WriteByte(ClientGlobals.cls.netchan.message, ClientCommands.CLC_STRINGCMD.value);
            MSG.WriteString(ClientGlobals.cls.netchan.message, "download "
                    + ClientGlobals.cls.downloadname);
        }

        ClientGlobals.cls.downloadnumber++;

        return false;
    }

    /*
     * ====================== CL_RegisterSounds ======================
     */
    public static void RegisterSounds() {
        S.BeginRegistration();
        CL_tent.RegisterTEntSounds();
        for (int i = 1; i < Defines.MAX_SOUNDS; i++) {
            if (ClientGlobals.cl.configstrings[Defines.CS_SOUNDS + i] == null
                    || ClientGlobals.cl.configstrings[Defines.CS_SOUNDS + i]
                            .equals("")
                    || ClientGlobals.cl.configstrings[Defines.CS_SOUNDS + i]
                            .equals("\0"))
                break;
            ClientGlobals.cl.sound_precache[i] = S
                    .RegisterSound(ClientGlobals.cl.configstrings[Defines.CS_SOUNDS
                            + i]);
            CL.SendKeyEvents(); // pump message loop
        }
        S.EndRegistration();
    }

    /*
     * ===================== CL_ParseDownload
     * 
     * A download message has been received from the server
     * =====================
     */
    public static void ParseDownload() {

        // read the data
        int size = MSG.ReadShort(Globals.net_message);
        int percent = MSG.ReadByte(Globals.net_message);
        if (size == -1) {
            Com.Printf("Server does not have this file.\n");
            if (ClientGlobals.cls.download != null) {
                // if here, we tried to resume a file but the server said no
                try {
                    ClientGlobals.cls.download.close();
                } catch (IOException e) {
                }
                ClientGlobals.cls.download = null;
            }
            CL.RequestNextDownload();
            return;
        }

        // open the file if not opened yet
        if (ClientGlobals.cls.download == null) {
            String name = DownloadFileName(ClientGlobals.cls.downloadtempname).toLowerCase();

            FS.CreatePath(name);

            ClientGlobals.cls.download = Lib.fopen(name, "rw");
            if (ClientGlobals.cls.download == null) {
                Globals.net_message.readcount += size;
                Com.Printf("Failed to open " + ClientGlobals.cls.downloadtempname
                        + "\n");
                CL.RequestNextDownload();
                return;
            }
        }


        try {
            ClientGlobals.cls.download.write(Globals.net_message.data,
                    Globals.net_message.readcount, size);
        } 
        catch (Exception e) {
        }
        Globals.net_message.readcount += size;

        if (percent != 100) {
            // request next block
            //	   change display routines by zoid
            ClientGlobals.cls.downloadpercent = percent;
            MSG.WriteByte(ClientGlobals.cls.netchan.message, ClientCommands.CLC_STRINGCMD.value);
            SZ.Print(ClientGlobals.cls.netchan.message, "nextdl");
        } else {
            try {
                ClientGlobals.cls.download.close();
            } 
            catch (IOException e) {
            }

            // rename the temp file to it's final name
            String oldn = DownloadFileName(ClientGlobals.cls.downloadtempname);
            String newn = DownloadFileName(ClientGlobals.cls.downloadname);
            int r = Lib.rename(oldn, newn);
            if (r != 0)
                Com.Printf("failed to rename.\n");

            ClientGlobals.cls.download = null;
            ClientGlobals.cls.downloadpercent = 0;

            // get another file if needed

            CL.RequestNextDownload();
        }
    }

    /*
     * =====================================================================
     * 
     * SERVER CONNECTING MESSAGES
     * 
     * =====================================================================
     */

    /*
     * ================== CL_ParseServerData ==================
     */
    //checked once, was ok.
    public static void ParseServerData() {
        Com.DPrintf("ParseServerData():Serverdata packet received.\n");
        //
        //	   wipe the client_state_t struct
        //
        CL.ClearState();
        ClientGlobals.cls.state = Defines.ca_connected;

        //	   parse protocol version number
        int i = MSG.ReadLong(Globals.net_message);
        ClientGlobals.cls.serverProtocol = i;

        // BIG HACK to let demos from release work with the 3.0x patch!!!
        if (Globals.server_state != ServerStates.SS_DEAD && Defines.PROTOCOL_VERSION == 34) {
        } else if (i != Defines.PROTOCOL_VERSION)
            Com.Error(Defines.ERR_DROP, "Server returned version " + i
                    + ", not " + Defines.PROTOCOL_VERSION);

        ClientGlobals.cl.servercount = MSG.ReadLong(Globals.net_message);
        ClientGlobals.cl.attractloop = MSG.ReadByte(Globals.net_message) != 0;

        // game directory
        String str = MSG.ReadString(Globals.net_message);
        ClientGlobals.cl.gamedir = str;
        Com.dprintln("gamedir=" + str);

        // set gamedir
        if (str.length() > 0
                && (FS.fs_gamedirvar.string == null
                        || FS.fs_gamedirvar.string.length() == 0 || FS.fs_gamedirvar.string
                        .equals(str))
                || (str.length() == 0 && (FS.fs_gamedirvar.string != null || FS.fs_gamedirvar.string
                        .length() == 0)))
            Cvar.Set("game", str);

        // parse player entity number
        ClientGlobals.cl.playernum = MSG.ReadShort(Globals.net_message);
        Com.dprintln("numplayers=" + ClientGlobals.cl.playernum);
        // get the full level name
        str = MSG.ReadString(Globals.net_message);
        Com.dprintln("levelname=" + str);

        if (ClientGlobals.cl.playernum == -1) { // playing a cinematic or showing a
            // pic, not a level
            SCR.PlayCinematic(str);
        } else {
            // seperate the printfs so the server message can have a color
            //			Com.Printf(
            //				"\n\n\35\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\37\n\n");
            //			Com.Printf('\02' + str + "\n");
            Com.Printf("Levelname:" + str + "\n");
            // need to prep refresh at next oportunity
            ClientGlobals.cl.refresh_prepped = false;
        }
    }

    /*
     * ================== CL_ParseBaseline ==================
     */
    public static void ParseBaseline() {
        entity_state_t nullstate = new entity_state_t(null);
        //memset(nullstate, 0, sizeof(nullstate));
        int bits[] = { 0 };
        int newnum = CL_ents.ParseEntityBits(bits);
        entity_state_t es = ClientGlobals.cl_entities[newnum].baseline;
        CL_ents.ParseDelta(nullstate, es, newnum, bits[0]);
    }

    /*
     * ================ CL_LoadClientinfo
     * 
     * ================
     */
    public static void LoadClientinfo(clientinfo_t ci, String s) {
        //char model_name[MAX_QPATH];
        //char skin_name[MAX_QPATH];
        //char model_filename[MAX_QPATH];
        //char skin_filename[MAX_QPATH];
        //char weapon_filename[MAX_QPATH];

        String model_name, skin_name, model_filename, skin_filename, weapon_filename;

        ci.cinfo = s;
        //ci.cinfo[sizeof(ci.cinfo) - 1] = 0;

        // isolate the player's name
        ci.name = s;
        //ci.name[sizeof(ci.name) - 1] = 0;

        int t = s.indexOf('\\');
        //t = strstr(s, "\\");

        if (t != -1) {
            ci.name = s.substring(0, t);
            s = s.substring(t + 1, s.length());
            //s = t + 1;
        }

        if (ClientGlobals.cl_noskins.value != 0 || s.length() == 0) {

            model_filename = ("players/male/tris.md2");
            weapon_filename = ("players/male/weapon.md2");
            skin_filename = ("players/male/grunt.pcx");
            ci.iconname = ("/players/male/grunt_i.pcx");

            ci.model = ClientGlobals.re.RegisterModel(model_filename);

            ci.weaponmodel = new model_t[Defines.MAX_CLIENTWEAPONMODELS];
            ci.weaponmodel[0] = ClientGlobals.re.RegisterModel(weapon_filename);
            ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);
            ci.icon = ClientGlobals.re.RegisterPic(ci.iconname);
            
        } else {
            // isolate the model name

            int pos = s.indexOf('/');

            if (pos == -1)
                pos = s.indexOf('/');
            if (pos == -1) {
                pos = 0;
                Com.Error(Defines.ERR_FATAL, "Invalid model name:" + s);
            }

            model_name = s.substring(0, pos);

            // isolate the skin name
            skin_name = s.substring(pos + 1, s.length());

            // model file
            model_filename = "players/" + model_name + "/tris.md2";
            ci.model = ClientGlobals.re.RegisterModel(model_filename);

            if (ci.model == null) {
                model_name = "male";
                model_filename = "players/male/tris.md2";
                ci.model = ClientGlobals.re.RegisterModel(model_filename);
            }

            // skin file
            skin_filename = "players/" + model_name + "/" + skin_name + ".pcx";
            ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);

            // if we don't have the skin and the model wasn't male,
            // see if the male has it (this is for CTF's skins)
            if (ci.skin == null && !model_name.equalsIgnoreCase("male")) {
                // change model to male
                model_name = "male";
                model_filename = "players/male/tris.md2";
                ci.model = ClientGlobals.re.RegisterModel(model_filename);

                // see if the skin exists for the male model
                skin_filename = "players/" + model_name + "/" + skin_name
                        + ".pcx";
                ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);
            }

            // if we still don't have a skin, it means that the male model
            // didn't have
            // it, so default to grunt
            if (ci.skin == null) {
                // see if the skin exists for the male model
                skin_filename = "players/" + model_name + "/grunt.pcx";
                ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);
            }

            // weapon file
            for (int i = 0; i < CL_view.num_cl_weaponmodels; i++) {
                weapon_filename = "players/" + model_name + "/"
                        + CL_view.cl_weaponmodels[i];
                ci.weaponmodel[i] = ClientGlobals.re.RegisterModel(weapon_filename);
                if (null == ci.weaponmodel[i] && model_name.equals("cyborg")) {
                    // try male
                    weapon_filename = "players/male/"
                            + CL_view.cl_weaponmodels[i];
                    ci.weaponmodel[i] = ClientGlobals.re
                            .RegisterModel(weapon_filename);
                }
                if (0 == ClientGlobals.cl_vwep.value)
                    break; // only one when vwep is off
            }

            // icon file
            ci.iconname = "/players/" + model_name + "/" + skin_name + "_i.pcx";
            ci.icon = ClientGlobals.re.RegisterPic(ci.iconname);
        }

        // must have loaded all data types to be valud
        if (ci.skin == null || ci.icon == null || ci.model == null
                || ci.weaponmodel[0] == null) {
            ci.skin = null;
            ci.icon = null;
            ci.model = null;
            ci.weaponmodel[0] = null;
            return;
        }
    }

    /*
     * ================ CL_ParseClientinfo
     * 
     * Load the skin, icon, and model for a client ================
     */
    public static void ParseClientinfo(int player) {
        String s = ClientGlobals.cl.configstrings[player + Defines.CS_PLAYERSKINS];

        clientinfo_t ci = ClientGlobals.cl.clientinfo[player];

        LoadClientinfo(ci, s);
    }

    /*
     * ================ CL_ParseConfigString ================
     */
    public static void ParseConfigString() {
        int i = MSG.ReadShort(Globals.net_message);

        if (i < 0 || i >= Defines.MAX_CONFIGSTRINGS)
            Com.Error(Defines.ERR_DROP, "configstring > MAX_CONFIGSTRINGS");

        String s = MSG.ReadString(Globals.net_message);

        String olds = ClientGlobals.cl.configstrings[i];
        ClientGlobals.cl.configstrings[i] = s;
        
        //Com.dprintln("ParseConfigString(): configstring[" + i + "]=<"+s+">");

        // do something apropriate

        if (i >= Defines.CS_LIGHTS
                && i < Defines.CS_LIGHTS + Defines.MAX_LIGHTSTYLES) {
            
            CL_fx.SetLightstyle(i - Defines.CS_LIGHTS);
            
        } else if (i == Defines.CS_CDTRACK) {
        	if (ClientGlobals.cl.refresh_prepped)
        		CDAudio.Play(Lib.atoi(ClientGlobals.cl.configstrings[Defines.CS_CDTRACK]), true);
        	
        } else if (i >= Defines.CS_MODELS && i < Defines.CS_MODELS + Defines.MAX_MODELS) {
            if (ClientGlobals.cl.refresh_prepped) {
                ClientGlobals.cl.model_draw[i - Defines.CS_MODELS] = ClientGlobals.re
                        .RegisterModel(ClientGlobals.cl.configstrings[i]);
                if (ClientGlobals.cl.configstrings[i].startsWith("*"))
                    ClientGlobals.cl.model_clip[i - Defines.CS_MODELS] = CM
                            .InlineModel(ClientGlobals.cl.configstrings[i]);
                else
                    ClientGlobals.cl.model_clip[i - Defines.CS_MODELS] = null;
            }
        } else if (i >= Defines.CS_SOUNDS
                && i < Defines.CS_SOUNDS + Defines.MAX_MODELS) {
            if (ClientGlobals.cl.refresh_prepped)
                ClientGlobals.cl.sound_precache[i - Defines.CS_SOUNDS] = S
                        .RegisterSound(ClientGlobals.cl.configstrings[i]);
        } else if (i >= Defines.CS_IMAGES
                && i < Defines.CS_IMAGES + Defines.MAX_MODELS) {
            if (ClientGlobals.cl.refresh_prepped)
                ClientGlobals.cl.image_precache[i - Defines.CS_IMAGES] = ClientGlobals.re
                        .RegisterPic(ClientGlobals.cl.configstrings[i]);
        } else if (i >= Defines.CS_PLAYERSKINS
                && i < Defines.CS_PLAYERSKINS + Defines.MAX_CLIENTS) {
            if (ClientGlobals.cl.refresh_prepped && !olds.equals(s))
                ParseClientinfo(i - Defines.CS_PLAYERSKINS);
        }
    }

    /*
     * =====================================================================
     * 
     * ACTION MESSAGES
     * 
     * =====================================================================
     */

    private static final float[] pos_v = { 0, 0, 0 };
    /*
     * ================== CL_ParseStartSoundPacket ==================
     */
    public static void ParseStartSoundPacket() {
        int flags = MSG.ReadByte(Globals.net_message);
        int sound_num = MSG.ReadByte(Globals.net_message);

        float volume;
        if ((flags & Defines.SND_VOLUME) != 0)
            volume = MSG.ReadByte(Globals.net_message) / 255.0f;
        else
            volume = Defines.DEFAULT_SOUND_PACKET_VOLUME;

        float attenuation;
        if ((flags & Defines.SND_ATTENUATION) != 0)
            attenuation = MSG.ReadByte(Globals.net_message) / 64.0f;
        else
            attenuation = Defines.DEFAULT_SOUND_PACKET_ATTENUATION;

        float ofs;
        if ((flags & Defines.SND_OFFSET) != 0)
            ofs = MSG.ReadByte(Globals.net_message) / 1000.0f;
        else
            ofs = 0;

        int channel;
        int ent;
        if ((flags & Defines.SND_ENT) != 0) { // entity reletive
            channel = MSG.ReadShort(Globals.net_message);
            ent = channel >> 3;
            if (ent > Defines.MAX_EDICTS)
                Com.Error(Defines.ERR_DROP, "CL_ParseStartSoundPacket: ent = "
                        + ent);

            channel &= 7;
        } else {
            ent = 0;
            channel = 0;
        }

        float pos[];
        if ((flags & Defines.SND_POS) != 0) { // positioned in space
            MSG.ReadPos(Globals.net_message, pos_v);
            // is ok. sound driver copies
            pos = pos_v;
        } else
            // use entity number
            pos = null;

        if (null == ClientGlobals.cl.sound_precache[sound_num])
            return;

        S.StartSound(pos, ent, channel, ClientGlobals.cl.sound_precache[sound_num],
                volume, attenuation, ofs);
    }

    public static void SHOWNET(String s) {
        if (ClientGlobals.cl_shownet.value >= 2)
            Com.Printf(Globals.net_message.readcount - 1 + ":" + s + "\n");
    }

    /*
     * ===================== CL_ParseServerMessage =====================
     */
    public static void ParseServerMessage() {
        //
        //	   if recording demos, copy the message out
        //
        //if (cl_shownet.value == 1)
        //Com.Printf(net_message.cursize + " ");
        //else if (cl_shownet.value >= 2)
        //Com.Printf("------------------\n");

        //
        //	   parse the message
        //
        while (true) {
            if (Globals.net_message.readcount > Globals.net_message.cursize) {
                Com.Error(Defines.ERR_FATAL,
                        "CL_ParseServerMessage: Bad server message:");
                break;
            }

            int cmd = MSG.ReadByte(Globals.net_message);

            if (cmd == -1) {
                SHOWNET("END OF MESSAGE");
                break;
            }

            if (ClientGlobals.cl_shownet.value >= 2) {
                if (null == svc_strings[cmd])
                    Com.Printf(Globals.net_message.readcount - 1 + ":BAD CMD "
                            + cmd + "\n");
                else
                    SHOWNET(svc_strings[cmd]);
            }

            // other commands
            switch (cmd) {
            default:
                Com.Error(Defines.ERR_DROP,
                        "CL_ParseServerMessage: Illegible server message\n");
                break;

            case NetworkCommands.svc_nop:
                //				Com.Printf ("svc_nop\n");
                break;

            case NetworkCommands.svc_disconnect:
                Com.Error(Defines.ERR_DISCONNECT, "Server disconnected\n");
                break;

            case NetworkCommands.svc_reconnect:
                Com.Printf("Server disconnected, reconnecting\n");
                if (ClientGlobals.cls.download != null) {
                    //ZOID, close download
                    try {
                        ClientGlobals.cls.download.close();
                    } catch (IOException e) {
                    }
                    ClientGlobals.cls.download = null;
                }
                ClientGlobals.cls.state = Defines.ca_connecting;
                ClientGlobals.cls.connect_time = -99999; // CL_CheckForResend() will
                // fire immediately
                break;

            case NetworkCommands.svc_print:
                int i = MSG.ReadByte(Globals.net_message);
                if (i == Defines.PRINT_CHAT) {
                    S.StartLocalSound("misc/talk.wav");
                    ClientGlobals.con.ormask = 128;
                }
                Com.Printf(MSG.ReadString(Globals.net_message));
                ClientGlobals.con.ormask = 0;
                break;

            case NetworkCommands.svc_centerprint:
                SCR.CenterPrint(MSG.ReadString(Globals.net_message));
                break;

            case NetworkCommands.svc_stufftext:
                String s = MSG.ReadString(Globals.net_message);
                Com.DPrintf("stufftext: " + s + "\n");
                Cbuf.AddText(s);
                break;

            case NetworkCommands.svc_serverdata:
                Cbuf.Execute(); // make sure any stuffed commands are done
                ParseServerData();
                break;

            case NetworkCommands.svc_configstring:
                ParseConfigString();
                break;

            case NetworkCommands.svc_sound:
                ParseStartSoundPacket();
                break;

            case NetworkCommands.svc_spawnbaseline:
                ParseBaseline();
                break;

            case NetworkCommands.svc_temp_entity:
                CL_tent.ParseTEnt();
                break;

            case NetworkCommands.svc_muzzleflash:
                CL_fx.ParseMuzzleFlash();
                break;

            case NetworkCommands.svc_muzzleflash2:
                CL_fx.ParseMuzzleFlash2();
                break;

            case NetworkCommands.svc_download:
                ParseDownload();
                break;

            case NetworkCommands.svc_frame:
                CL_ents.ParseFrame();
                break;

            case NetworkCommands.svc_inventory:
                CL_inv.ParseInventory();
                break;

            case NetworkCommands.svc_layout:
        	ClientGlobals.cl.layout = MSG.ReadString(Globals.net_message);
                break;

            case NetworkCommands.svc_playerinfo:
            case NetworkCommands.svc_packetentities:
            case NetworkCommands.svc_deltapacketentities:
                Com.Error(Defines.ERR_DROP, "Out of place frame data");
                break;
            }
        }

        CL_view.AddNetgraph();

        //
        // we don't know if it is ok to save a demo message until
        // after we have parsed the frame
        //
        if (ClientGlobals.cls.demorecording && !ClientGlobals.cls.demowaiting)
            CL.WriteDemoMessage();
    }
}