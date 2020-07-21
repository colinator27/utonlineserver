// Script by colinator27, designed to be used with server at https://github.com/colinator27/utonlineserver

EnsureDataLoaded();

ScriptMessage("Adds client-side code to see other players\nFor Undertale 1.08\nby colinator27");

// Make script references
var send_packet = new UndertaleCode() { Name = Data.Strings.MakeString("gml_Script_send_packet") };
Data.Code.Add(send_packet);
Data.CodeLocals.Add(new UndertaleCodeLocals() { Name = send_packet.Name });
Data.Scripts.Add(new UndertaleScript() { Name = Data.Strings.MakeString("send_packet"), Code = send_packet });
Data.Functions.EnsureDefined("send_packet", Data.Strings);

var process_packet = new UndertaleCode() { Name = Data.Strings.MakeString("gml_Script_process_packet") };
Data.Code.Add(process_packet);
Data.CodeLocals.Add(new UndertaleCodeLocals() { Name = process_packet.Name });
Data.Scripts.Add(new UndertaleScript() { Name = Data.Strings.MakeString("process_packet"), Code = process_packet });
Data.Functions.EnsureDefined("process_packet", Data.Strings);

// Client object
var obj_uto_client = new UndertaleGameObject()
{
    Name = Data.Strings.MakeString("obj_uto_client"),
    Visible = true,
	Persistent = true
};
Data.GameObjects.Add(obj_uto_client);
Data.Rooms.ByName("room_start").GameObjects.Add(new UndertaleRoom.GameObject()
{
    InstanceID = Data.GeneralInfo.LastObj++,
    ObjectDefinition = obj_uto_client,
    X = 0, Y = 0
});

obj_uto_client.EventHandlerFor(EventType.Create, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
ip = get_string_async(""What IP to connect to? (don't include port)"", ""127.0.0.1"");
port = -1;

playerId = -1;

state = 0; // 0 = logging in, 1 = logged in, -1 = kicked
connected = false;
disconnectTimer = 0;
", Data);

obj_uto_client.EventHandlerFor(EventType.Other, EventSubtypeOther.GameEnd, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
ds_map_destroy(otherPlayers);
", Data);

obj_uto_client.EventHandlerFor(EventType.Step, EventSubtypeStep.EndStep, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
if (state == 1)
{
	disconnectTimer++;
	if (disconnectTimer >= 4*room_speed)
	{
		state = -1;
		disconnectTimer = 0;
		connected = false;
		show_message_async(""Disconnected!"");
		playerId = -1;
		ds_map_clear(otherPlayers);
	}

	updatePacketTimer++;
	if (updatePacketTimer >= 0.05*room_speed)
	{
		updatePacketTimer = 0;
		if (connected && state == 1)
		{
			if (!instance_exists(obj_mainchara))
			{
				if (prevRoom != -1)
				{
					ds_map_clear(otherPlayers);
				
					// Send change room packet (go into no room)
					var b = buffer_create(16+2+2+2+4+4, buffer_fixed, 1);
					for (var i = 0; i < 16; i++)
						buffer_write(b, buffer_s8, uuid[i]);
					buffer_write(b, buffer_s16, -1);
					buffer_write(b, buffer_s16, prevSpr);
					buffer_write(b, buffer_s16, prevSprInd);
					buffer_write(b, buffer_f32, prevX);
					buffer_write(b, buffer_f32, prevY);
					send_packet(10, b);
					buffer_delete(b);
					prevRoom = -1;
				}
			} else if (obj_mainchara.sprite_index > 0)
			{
				if (prevRoom != room)
				{
					ds_map_clear(otherPlayers);
				
					// Send change room packet
					var b = buffer_create(16+2+2+2+4+4, buffer_fixed, 1);
					for (var i = 0; i < 16; i++)
						buffer_write(b, buffer_s8, uuid[i]);
					buffer_write(b, buffer_s16, room);
					buffer_write(b, buffer_s16, obj_mainchara.sprite_index);
					buffer_write(b, buffer_s16, obj_mainchara.image_index);
					buffer_write(b, buffer_f32, obj_mainchara.x);
					buffer_write(b, buffer_f32, obj_mainchara.y);
					send_packet(10, b);
					buffer_delete(b);
					prevRoom = room;
				} else if (prevX != obj_mainchara.x || prevY != obj_mainchara.y ||
						   prevSpr != obj_mainchara.sprite_index || prevSprInd != obj_mainchara.image_index)
				{
					// Send movement/visual packet
					var b = buffer_create(16+2+2+4+4, buffer_fixed, 1);
					for (var i = 0; i < 16; i++)
						buffer_write(b, buffer_s8, uuid[i]);
					buffer_write(b, buffer_s16, obj_mainchara.sprite_index);
					buffer_write(b, buffer_s16, obj_mainchara.image_index);
					buffer_write(b, buffer_f32, obj_mainchara.x);
					buffer_write(b, buffer_f32, obj_mainchara.y);
					send_packet(11, b);
					buffer_delete(b);
				}
			
				prevX = obj_mainchara.x;
				prevY = obj_mainchara.y;
				prevSpr = obj_mainchara.sprite_index;
				prevSprInd = obj_mainchara.image_index;
			}
		}
	}
} else if (state == 0 && connected)
{
	disconnectTimer++;
	if (disconnectTimer >= 4*room_speed)
	{
		state = -1;
		disconnectTimer = 0;
		connected = false;
		show_message_async(""Failed to connect!"");
	}
}
", Data);

obj_uto_client.EventHandlerFor(EventType.Alarm, (uint)0, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
if (state != -1)
{
	var b = buffer_create(16, buffer_fixed, 1);
	for (var i = 0; i < 16; i++)
		buffer_write(b, buffer_s8, uuid[i]);
	send_packet(2, b);
	buffer_delete(b);
	alarm[0] = 2*room_speed;
}
", Data);

obj_uto_client.EventHandlerFor(EventType.Draw, EventSubtypeDraw.DrawGUI, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
draw_set_color(c_yellow);
draw_set_font(fnt_main);
draw_text(5, 5, playerId);
", Data);

obj_uto_client.EventHandlerFor(EventType.Other, EventSubtypeOther.AsyncDialog, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
if (port == -1)
{
	if (ds_map_find_value(async_load, ""id"") == ip)
	{
		ip = ds_map_find_value(async_load, ""result"");
		port = get_integer_async(""What port to connect to?"", 1337);
	}
} else if (ds_map_find_value(async_load, ""id"") == port)
{
	port = ds_map_find_value(async_load, ""value"");
		
	network_set_config(network_config_connect_timeout, 4000);
	network_set_config(network_config_use_non_blocking_socket, 1);

	var buff = buffer_create(1, buffer_fixed, 1);

	socket = network_create_socket(network_socket_udp);

	send_packet(1, buff); // login

	buffer_delete(buff);

	uuid = array_create(16, 0);

	updatePacketTimer = 0;
	prevRoom = 0;
	prevX = 0;
	prevY = 0;
	prevSpr = spr_maincharad;
	prevSprInd = 0;

	otherPlayers = ds_map_create();

	connected = true;
	disconnectTimer = 0;
	
	alarm[0] = 2*room_speed;
}
", Data);

obj_uto_client.EventHandlerFor(EventType.Other, EventSubtypeOther.AsyncNetworking, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
switch (ds_map_find_value(async_load, ""type""))
{
	case network_type_data:
		if (ds_map_find_value(async_load, ""id"") == socket)
		{
			process_packet(ds_map_find_value(async_load, ""buffer""), ds_map_find_value(async_load, ""size"")); 
		}
		break;
}
", Data);

// Other player objects
var obj_mainchara_other = new UndertaleGameObject()
{
    Name = Data.Strings.MakeString("obj_mainchara_other"),
    Visible = true
};
Data.GameObjects.Add(obj_mainchara_other);

obj_mainchara_other.EventHandlerFor(EventType.Create, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
playerId = -1;
image_alpha = 0.5;
image_speed = 0;
targetX = x;
targetY = y;
", Data);

obj_mainchara_other.EventHandlerFor(EventType.Alarm, (uint)0, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
x = targetX;
y = targetY;
", Data);

obj_mainchara_other.EventHandlerFor(EventType.Step, EventSubtypeStep.Step, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
scr_depth();
", Data);

obj_mainchara_other.EventHandlerFor(EventType.Draw, EventSubtypeDraw.Draw, Data.Strings, Data.Code, Data.CodeLocals).AppendGML(@"
draw_self();
draw_set_alpha(0.75);
draw_set_color(c_white);
draw_set_font(fnt_maintext);
draw_text(x + 6, y - 10, string(playerId));
draw_set_alpha(1);
", Data);

// Actually add script contents
send_packet.AppendGML(@"
var srcSize = buffer_get_size(argument1);
var s = srcSize + 5;
var b = buffer_create(s, buffer_fixed, 1);
buffer_write(b, buffer_u8, ord(""U""));
buffer_write(b, buffer_u8, ord(""T""));
buffer_write(b, buffer_u8, ord(""O""));
buffer_write(b, buffer_u8, 0);
buffer_write(b, buffer_u8, argument0);
buffer_copy(argument1, 0, srcSize, b, 5);
network_send_udp_raw(obj_uto_client.socket, obj_uto_client.ip, obj_uto_client.port, b, s);
buffer_delete(b);
", Data);

process_packet.AppendGML(@"
var buff = argument0;
var size = argument1;

if (size < 5) return;
if (buffer_read(buff, buffer_u8) != ord(""U"")) return;
if (buffer_read(buff, buffer_u8) != ord(""T"")) return;
if (buffer_read(buff, buffer_u8) != ord(""O"")) return;
if (buffer_read(buff, buffer_u8) != 0) return;

switch (buffer_read(buff, buffer_u8))
{
	case 1: // UUID
		if (size < 5+16 || obj_uto_client.state != 0) return;
		
		obj_uto_client.playerId = buffer_read(buff, buffer_s32);
		for (var i = 0; i < 16; i++)
			obj_uto_client.uuid[i] = buffer_read(buff, buffer_s8);
		show_message_async(""Connected!"");
		obj_uto_client.connected = true;
		obj_uto_client.disconnectTimer = 0;
		state = 1;
		break;
	case 2: // Heartbeat response
		if (obj_uto_client.state != 1) return;
		
		obj_uto_client.connected = true;
		obj_uto_client.disconnectTimer = 0;
		break;
	case 10: // Player(s) join room
		if (size < 5+4+2 || obj_uto_client.state != 1) return;
		if (buffer_read(buff, buffer_s32) != room) return;
		var count = buffer_read(buff, buffer_s16);
		if (size < 5+4+2+(count*(4+2+2+4+4))) return;
		for (var i = 0; i < count; i++)
		{
			var pid = buffer_read(buff, buffer_s32);
			if (ds_map_exists(obj_uto_client.otherPlayers, pid))
				instance_destroy(ds_map_find_value(obj_uto_client.otherPlayers, pid));
			var sprInd = buffer_read(buff, buffer_s16);
			var imgInd = buffer_read(buff, buffer_s16);
			var px = buffer_read(buff, buffer_f32);
			var py = buffer_read(buff, buffer_f32);
			var inst = instance_create(px, py, obj_mainchara_other);
			inst.playerId = pid;
			inst.sprite_index = sprInd;
			inst.image_index = imgInd;
			inst.targetX = inst.x;
			inst.targetY = inst.y;
			ds_map_set(obj_uto_client.otherPlayers, pid, inst);
		}
		break;
	case 11: // Player leaves room
		if (size < 5+4+4 || obj_uto_client.state != 1) return;
		if (buffer_read(buff, buffer_s32) != room) return;
		var pid = buffer_read(buff, buffer_s32);
		if (ds_map_exists(obj_uto_client.otherPlayers, pid))
		{
			instance_destroy(ds_map_find_value(obj_uto_client.otherPlayers, pid));
			ds_map_delete(obj_uto_client.otherPlayers, pid);
		}
		break;
	case 12: // Player in room visual changes
		if (size < 5+8+4+4+2+2+4+4 || obj_uto_client.state != 1) return;
		var timestamp = buffer_read(buff, buffer_u64); // TODO: don't ignore this?
		if (buffer_read(buff, buffer_s32) != room) return;
		var pid = buffer_read(buff, buffer_s32);
		if (ds_map_exists(obj_uto_client.otherPlayers, pid))
		{			
			var inst = ds_map_find_value(obj_uto_client.otherPlayers, pid);
			inst.sprite_index = buffer_read(buff, buffer_s16);
			inst.image_index = buffer_read(buff, buffer_s16);
			inst.targetX = buffer_read(buff, buffer_f32);
			inst.targetY = buffer_read(buff, buffer_f32);
			with (inst)
			{
				x = (x + targetX) / 2;
				y = (y + targetY) / 2;
				alarm[0] = 2;
			}
		}
		break;
	case 254: // Reset position (due to lag/anticheat)
		if (size < 5+4+4 || obj_uto_client.state != 1) return;
		if (instance_exists(obj_mainchara))
		{
			obj_mainchara.x = buffer_read(buff, buffer_f32);
			obj_mainchara.y = buffer_read(buff, buffer_f32);
		}
		break;
	case 255: // Kick message from server
		if (size < 6 || obj_uto_client.state == -1) return;
		
		show_message_async(""Kicked from server! Message: "" + buffer_read(buff, buffer_string));
		obj_uto_client.state = -1;
		obj_uto_client.playerId = -1;
		break;
}
", Data);

ChangeSelection(obj_uto_client);

ScriptMessage("Done!");
