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

// Created on 02.01.2004 by RST.
// $Id: dareaportal_t.java,v 1.2 2004-01-02 22:29:01 rst Exp $

package jake2.qcommon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// import jake2.*;
// import jake2.client.*;
// import jake2.game.*;
// import jake2.qcommon.*;
// import jake2.render.*;
// import jake2.server.*;

// each area has a list of portals that lead into other areas
// when portals are closed, other areas may not be visible or
// hearable even if the vis info says that it should be

public class dareaportal_t {

	public dareaportal_t() {
	}

	public dareaportal_t(ByteBuffer bb) {
		bb.order(ByteOrder.LITTLE_ENDIAN);

		portalnum = bb.getShort();
		otherarea = bb.getShort();
	}

	int portalnum;
	int otherarea;
	
	public static int SIZE = 8;
}