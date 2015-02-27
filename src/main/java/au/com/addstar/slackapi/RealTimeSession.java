package au.com.addstar.slackapi;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import lombok.Getter;
import au.com.addstar.slackapi.Message.MessageType;
import au.com.addstar.slackapi.events.MessageEvent;
import au.com.addstar.slackapi.events.RealTimeEvent;
import au.com.addstar.slackapi.exceptions.SlackRTException;
import au.com.addstar.slackapi.internal.RTMEvent;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class RealTimeSession implements Closeable
{
	private Gson gson;
	
	@Getter
	private User self;
	private Set<User> users;
	private Set<Channel> channels;
	private Set<Group> groups;
	
	private Map<String, User> userMap;
	private Map<String, Channel> channelMap;
	private Map<String, Group> groupMap;
	
	private Map<String, User> userIdMap;
	private Map<String, Channel> channelIdMap;
	private Map<String, Group> groupIdMap;
	
	private WebSocketClient client;
	private int nextMessageId = 1;
	private boolean needJoinConfirm;
	
	private List<RealTimeListener> listeners;
	
	RealTimeSession(JsonObject object, SlackAPI main) throws IOException
	{
		gson = main.getGson();
		
		listeners = Lists.newArrayList();
		
		load(object);
		
		initWebSocket(object.get("url").getAsString());
	}
	
	public void addListener(RealTimeListener listener)
	{
		synchronized(listeners)
		{
			listeners.add(listener);
		}
	}
	
	public void removeListener(RealTimeListener listener)
	{
		synchronized(listeners)
		{
			listeners.remove(listener);
		}
	}
	
	private void postLogin()
	{
		synchronized(listeners)
		{
			for (RealTimeListener listener : listeners)
			{
				listener.onLoginComplete();
			}
		}
	}
	
	private void postError(SlackRTException ex)
	{
		synchronized(listeners)
		{
			for (RealTimeListener listener : listeners)
			{
				listener.onError(ex);;
			}
		}
	}
	
	private void postEvent(RealTimeEvent event)
	{
		synchronized(listeners)
		{
			for (RealTimeListener listener : listeners)
			{
				listener.onEvent(event);
			}
		}
	}
	
	private void load(JsonObject object)
	{
		JsonObject self = object.getAsJsonObject("self");
		JsonArray channels = object.getAsJsonArray("channels");
		JsonArray groups = object.getAsJsonArray("groups");
		JsonArray users = object.getAsJsonArray("users");
		
		String selfId = self.get("id").getAsString();
		
		// Load users
		this.users = Sets.newHashSetWithExpectedSize(users.size());
		userMap = Maps.newHashMapWithExpectedSize(users.size());
		userIdMap = Maps.newHashMapWithExpectedSize(users.size());
		for (JsonElement user : users)
		{
			User loaded = gson.fromJson(user, User.class);
			if (loaded.getId().equals(selfId))
				this.self = loaded;
			
			addUser(loaded);
		}
		
		// Load channels
		this.channels = Sets.newHashSetWithExpectedSize(channels.size());
		channelMap = Maps.newHashMapWithExpectedSize(channels.size());
		channelIdMap = Maps.newHashMapWithExpectedSize(channels.size());
		for (JsonElement channel : channels)
		{
			Channel loaded = gson.fromJson(channel, Channel.class);
			addChannel(loaded);
		}
		
		// Load groups
		this.groups = Sets.newHashSetWithExpectedSize(groups.size());
		groupMap = Maps.newHashMapWithExpectedSize(groups.size());
		groupIdMap = Maps.newHashMapWithExpectedSize(groups.size());
		for (JsonElement group : groups)
		{
			Group loaded = gson.fromJson(group, Group.class);
			addGroup(loaded);
		}
	}
	
	private void initWebSocket(String url) throws IOException
	{
		try
		{
			URI uri = new URI(url);
			needJoinConfirm = true;
			client = new WebSocketClient(new SslContextFactory());
			client.start();
			client.connect(new SocketClient(), uri);
			nextMessageId = 1;
		}
		catch ( URISyntaxException e )
		{
			// Should never happen
			return;
		}
		catch (IOException e)
		{
			throw e;
		}
		// Sigh, couldnt they pick a more specific one? :/
		catch ( Exception e )
		{
			throw new IOException(e);
		}
	}
	
	private void addUser(User user)
	{
		users.add(user);
		userMap.put(user.getName().toLowerCase(), user);
		userIdMap.put(user.getId(), user);
	}
	
	public Set<User> getUsers()
	{
		return Collections.unmodifiableSet(users);
	}
	
	public User getUser(String name)
	{
		return userMap.get(name.toLowerCase());
	}
	
	public User getUserById(String id)
	{
		return userIdMap.get(id);
	}
	
	private void addChannel(Channel channel)
	{
		channels.add(channel);
		channelMap.put(channel.getName().toLowerCase(), channel);
		channelIdMap.put(channel.getId(), channel);
	}
	
	public Set<Channel> getChannels()
	{
		return Collections.unmodifiableSet(channels);
	}
	
	public Channel getChannel(String name)
	{
		return channelMap.get(name.toLowerCase());
	}
	
	public Channel getChannelById(String id)
	{
		return channelIdMap.get(id);
	}
	
	private void addGroup(Group group)
	{
		groups.add(group);
		groupMap.put(group.getName().toLowerCase(), group);
		groupIdMap.put(group.getId(), group);
	}
	
	public Set<Group> getGroups()
	{
		return Collections.unmodifiableSet(groups);
	}
	
	public Group getGroup(String name)
	{
		return groupMap.get(name.toLowerCase());
	}
	
	public Group getGroupById(String id)
	{
		return groupIdMap.get(id);
	}
	
	public boolean isOpen()
	{
		return client != null && client.isRunning();
	}
	
	@Override
	public void close()
	{
		try
		{
			client.stop();
			client = null;
		}
		catch ( Exception e )
		{
			// Its shutting down, I dont care
		}
	}
	
	private SlackRTException makeException(JsonObject object)
	{
		if (object.has("error"))
		{
			JsonObject error = object.getAsJsonObject("error");
			return new SlackRTException(error.get("code").getAsInt(), error.get("msg").getAsString());
		}
		return null;
	}
	
	private void onEvent(RTMEvent event)
	{
		// Handle login first
		if (needJoinConfirm)
		{
			if (event.getType().equals("hello"))
			{
				needJoinConfirm = false;
				postLogin();
			}
			else
			{
				postError(makeException(event.getData()));
				close();
				return;
			}
			
			return;
		}
		
		RealTimeEvent newEvent = null;
		switch (event.getType())
		{
		case "message":
		{
			Message message = gson.fromJson(event.getData(), Message.class);
			User user;
			if (message.getSubtype() == MessageType.Edit)
				user = getUserById(message.getEditUserId());
			else
				user = getUserById(message.getUserId());
			
			newEvent = new MessageEvent(user, message, message.getSubtype());
			break;
		}
		case "channel_created":
			break;
		case "channel_joined":
			break;
		case "channel_left":
			break;
		case "channel_rename":
			break;
		case "channel_archive":
			break;
		case "channel_unarchive":
			break;
		case "channel_history_changed":
			break;
		case "group_joined":
			break;
		case "group_left":
			break;
		case "group_open":
			break;
		case "group_close":
			break;
		case "group_archive":
			break;
		case "group_unarchive":
			break;
		case "group_rename":
			break;
		case "group_history_changed":
			break;
		case "user_change":
			break;
		case "team_join":
			break;
		case "error":
			postError(makeException(event.getData()));
			break;
		}
		
		if (newEvent != null)
			postEvent(newEvent);
	}
	
	private class SocketClient implements WebSocketListener
	{
		@Override
		public void onWebSocketBinary( byte[] payload, int offset, int len )
		{
		}

		@Override
		public void onWebSocketClose( int statusCode, String reason )
		{
			System.out.println("Got closed :" + statusCode + " " + reason);
		}

		@Override
		public void onWebSocketConnect( Session session )
		{
			System.out.println("Opened RTM");
		}

		@Override
		public void onWebSocketError( Throwable cause )
		{
			System.out.println("RTM Ex");
			cause.printStackTrace();
		}

		@Override
		public void onWebSocketText( String message )
		{
			RTMEvent event = gson.fromJson(message, RTMEvent.class);
			onEvent(event);
		}
	}
}
