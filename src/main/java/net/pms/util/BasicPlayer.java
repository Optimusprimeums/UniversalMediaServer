package net.pms.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import net.pms.configuration.DeviceConfiguration;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.RealFile;
import net.pms.dlna.virtual.VirtualVideoAction;
import static net.pms.network.UPNPHelper.unescape;
import org.apache.commons.lang3.StringUtils;
import java.util.regex.Matcher;
import net.pms.PMS;
import javax.swing.SwingUtilities;
import java.io.File;
import java.util.regex.Pattern;

public interface BasicPlayer extends ActionListener {
	public class State {
		public int playback;
		public boolean mute;
		public int volume;
		public String position, duration;
		public String name, uri, metadata;
		public long buffer;
	}

	final static int STOPPED = 0;
	final static int PLAYING = 1;
	final static int PAUSED = 2;
	final static int PLAYCONTROL = 1;
	final static int VOLUMECONTROL = 2;

	public void setURI(String uri, String metadata);

	public void pressPlay(String uri, String metadata);

	public void pressStop();

	public void play();

	public void pause();

	public void stop();

	public void next();

	public void prev();

	public void forward();

	public void rewind();

	public void mute();

	public void setVolume(int volume);

	public void add(int index, String uri, String name, String metadata, boolean select);

	public void remove(String uri);

	public void setBuffer(long mb);

	public State getState();

	public int getControls();

	public DefaultComboBoxModel getPlaylist();

	public void connect(ActionListener listener);

	public void disconnect(ActionListener listener);

	public void alert();

	public void start();

	public void reset();

	public void close();

	// An empty implementation with some basic funtionalities defined

	public static class Minimal implements BasicPlayer {

		public DeviceConfiguration renderer;
		protected State state;
		protected LinkedHashSet<ActionListener> listeners;

		public Minimal(DeviceConfiguration renderer) {
			this.renderer = renderer;
			state = new State();
			listeners = new LinkedHashSet();
			if (renderer.gui != null) {
				connect(renderer.gui);
			}
			reset();
		}

		@Override
		public void start() {
		}

		@Override
		public void reset() {
			state.playback = STOPPED;
			state.position = "";
			state.duration = "";
			state.name = " ";
			state.buffer = 0;
			alert();
		}

		@Override
		public void connect(ActionListener listener) {
			if (listener != null) {
				listeners.add(listener);
			}
		}

		@Override
		public void disconnect(ActionListener listener) {
			listeners.remove(listener);
			if (listeners.isEmpty()) {
				close();
			}
		}

		@Override
		public void alert() {
			for (ActionListener l : listeners) {
				l.actionPerformed(new ActionEvent(this, 0, null));
			}
		}

		@Override
		public BasicPlayer.State getState() {
			return state;
		}

		@Override
		public void close() {
			listeners.clear();
			renderer.setPlayer(null);
		}

		@Override
		public void setBuffer(long mb) {
			state.buffer = mb;
			alert();
		}

		@Override
		public void setURI(String uri, String metadata) {
		}

		@Override
		public void pressPlay(String uri, String metadata) {
		}

		@Override
		public void pressStop() {
		}

		@Override
		public void play() {
		}

		@Override
		public void pause() {
		}

		@Override
		public void stop() {
		}

		@Override
		public void next() {
		}

		@Override
		public void prev() {
		}

		@Override
		public void forward() {
		}

		@Override
		public void rewind() {
		}

		@Override
		public void mute() {
		}

		@Override
		public void setVolume(int volume) {
		}

		@Override
		public void add(int index, String uri, String name, String metadata, boolean select) {
		}

		@Override
		public void remove(String uri) {
		}

		@Override
		public int getControls() {
			return 0;
		}

		@Override
		public DefaultComboBoxModel getPlaylist() {
			return null;
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
		}
	}

	// An abstract implementation with all internal playback/playlist logic included.
	// Ideally the entire state-machine resides here and subclasses just implement the
	// details of communicating with the target device.

	public static abstract class Logical extends Minimal {
		public Playlist playlist;
		protected boolean autoContinue, addAllSiblings, forceStop;
		protected int lastPlayback;

		public Logical(DeviceConfiguration renderer) {
			super(renderer);
			playlist = new Playlist(this);
			lastPlayback = STOPPED;
			autoContinue = renderer.isAutoContinue();
			addAllSiblings = renderer.isAutoAddAll();
			forceStop = false;
			alert();
			initAutoPlay(this);
		}

		@Override
		public abstract void setURI(String uri, String metadata);

		public Playlist.Item resolveURI(String uri, String metadata) {
			if (uri != null) {
				Playlist.Item item;
				if (metadata != null && metadata.startsWith("<DIDL")) {
					// If it looks real assume it's valid
					return new Playlist.Item(uri, null, metadata);
				} else if ((item = playlist.get(uri)) != null) {
					// We've played it before
					return item;
				} else {
					// It's new to us, find or create the resource as required.
					// Note: here metadata (if any) is actually the resource name
					DLNAResource d = DLNAResource.getValidResource(uri, metadata, renderer);
					if (d != null) {
						return new Playlist.Item(d.getURL("", true), d.getDisplayName(), d.getDidlString(renderer));
					}
				}
			}
			return null;
		}

		@Override
		public void pressPlay(String uri, String metadata) {
			forceStop = false;
			if (state.playback == -1) {
				// unknown state, we assume it's stopped
				state.playback = STOPPED;
			}
			if (state.playback == PLAYING) {
				pause();
			} else {
				if (state.playback == STOPPED) {
					Playlist.Item item = playlist.resolve(uri);
					if (item != null) {
						uri = item.uri;
						metadata = item.metadata;
						state.name = item.name;
					}
					if (uri != null && !uri.equals(state.uri)) {
						setURI(uri, metadata);
					}
				}
				play();
			}
		}

		@Override
		public void pressStop() {
			forceStop = true;
			stop();
		}

		@Override
		public void next() {
			step(1);
		}

		@Override
		public void prev() {
			step(-1);
		}

		public void step(int n) {
			if (state.playback != STOPPED) {
				stop();
			}
			state.playback = STOPPED;
			playlist.step(n);
			pressPlay(null, null);
		}

		@Override
		public void alert() {
			boolean stopping = state.playback == STOPPED && lastPlayback != -1 && lastPlayback != STOPPED;
			lastPlayback = state.playback;
			super.alert();
			if (stopping && autoContinue && !forceStop) {
				next();
			}
		}

		@Override
		public int getControls() {
			return renderer.controls;
		}

		@Override
		public DefaultComboBoxModel getPlaylist() {
			return playlist;
		}

		@Override
		public void add(int index, String uri, String name, String metadata, boolean select) {
			if (!StringUtils.isBlank(uri)) {
				if (addAllSiblings && DLNAResource.isResourceUrl(uri)) {
					DLNAResource d = PMS.getGlobalRepo().get(DLNAResource.parseResourceId(uri));
					if (d != null && d.getParent() != null) {
						addAll(index, d.getParent().getChildren(), select);
						return;
					}
				}
				playlist.add(index, uri, name, metadata, select);
			}
		}

		public void addAll(int index, List<DLNAResource> list, boolean select) {
			for (DLNAResource r : list) {
				if ((r instanceof VirtualVideoAction) || r.isFolder()) {
					// skip these
					continue;
				}
				playlist.add(index, r.getURL("", true), r.getDisplayName(), r.getDidlString(renderer), select);
				select = false;
			}
		}

		@Override
		public void remove(String uri) {
			if (!StringUtils.isBlank(uri)) {
				playlist.remove(uri);
			}
		}

		private static void initAutoPlay(final BasicPlayer.Logical player) {
			String auto = player.renderer.getAutoPlay();
			if (StringUtils.isEmpty(auto)) {
				return;
			}
			String[] strs = auto.split(" ");
			for (String s : strs) {
				String[] tmp = s.split(":", 2);
				if (tmp.length != 2) {
					continue;
				}
				if (!player.renderer.getConfName().equalsIgnoreCase(tmp[0])) {
					continue;
				}
				final String folder = tmp[1];
				Runnable r = new Runnable() {
					@Override
					public void run() {
						while(PMS.get().getServer().getHost() == null) {
							try {
								Thread.sleep(1000);
							} catch (Exception e) {
								return;
							}
						}
						RealFile f = new RealFile(new File(folder));
						f.discoverChildren();
						f.analyzeChildren(-1);
						player.addAll(-1, f.getChildren(), true);
						// add a short delay here since player.add uses swing.invokelater
						try {
							Thread.sleep(1000);
						} catch (Exception e) {
						}
						player.pressPlay(null, null);
					}
				};
				new Thread(r).start();
			}
		}

		public static class Playlist extends DefaultComboBoxModel {
			private static final long serialVersionUID = 5934677633834195753L;

			Logical player;

			public Playlist(Logical p) {
				player = p;
			}

			public Item get(String uri) {
				int index = getIndexOf(new Item(uri, null, null));
				if (index > -1) {
					return (Item) getElementAt(index);
				}
				return null;
			}

			public Item resolve(String uri) {
				Item item = null;
				try {
					Object selected = getSelectedItem();
					Item selectedItem = selected instanceof Item ? (Item) selected : null;
					String selectedName = selectedItem != null ? selectedItem.name : null;
					// See if we have a matching item for the "uri", which could be:
					item = (Item) (
						// An alias for the currently selected item
						StringUtils.isBlank(uri) || uri.equals(selectedName) ? selectedItem :
						// An item index, e.g. '$i$4'
						uri.startsWith("$i$") ? getElementAt(Integer.valueOf(uri.substring(3))) :
						// Or an actual uri
						get(uri));
				} catch (Exception e) {
				}
				return (item != null && isValid(item, player.renderer)) ? item : null;
			}

			public static boolean isValid(Item item, DeviceConfiguration renderer) {
				if (DLNAResource.isResourceUrl(item.uri)) {
					// Check existence for resource uris
					if (PMS.get().getGlobalRepo().exists(DLNAResource.parseResourceId(item.uri))) {
						return true;
					}
					// Repair the item if possible
					DLNAResource d = DLNAResource.getValidResource(item.uri, item.name, renderer);
					if (d != null) {
						item.uri = d.getURL("", true);
						item.metadata = d.getDidlString(renderer);
						return true;
					}
					return false;
				}
				// Assume non-resource uris are valid
				return true;
			}

			public void validate() {
				for (int i = getSize() - 1; i > -1; i--) {
					if (!isValid((Item) getElementAt(i), player.renderer)) {
						removeElementAt(i);
					}
				}
			}

			public void set(String uri, String name, String metadata) {
				add(0, uri, name, metadata, true);
			}

			public void add(final int index, final String uri, final String name, final String metadata, final boolean select) {
				if (!StringUtils.isBlank(uri)) {
					// TODO: check headless mode (should work according to https://java.net/bugzilla/show_bug.cgi?id=2568)
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							Item item = resolve(uri);
							if (item == null) {
								item = new Item(uri, name, metadata);
								insertElementAt(item, index > -1 ? index : getSize());
							}
							if (select) {
								setSelectedItem(item);
							}
						}
					});
				}
			}

			public void remove(final String uri) {
				if (!StringUtils.isBlank(uri)) {
					// TODO: check headless mode
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							Item item = resolve(uri);
							if (item != null) {
								removeElement(item);
							}
						}
					});
				}
			}

			public void step(int n) {
				int i = (getIndexOf(getSelectedItem()) + getSize() + n) % getSize();
				setSelectedItem(getElementAt(i));
			}

			public static class Item {

				public String name, uri, metadata;
				static final Matcher dctitle = Pattern.compile("<dc:title>(.+)</dc:title>").matcher("");

				public Item(String uri, String name, String metadata) {
					this.uri = uri;
					this.name = name;
					this.metadata = metadata;
				}

				@Override
				public String toString() {
					if (StringUtils.isBlank(name)) {
						name = (! StringUtils.isEmpty(metadata) && dctitle.reset(unescape(metadata)).find()) ?
							dctitle.group(1) :
							new File(StringUtils.substringBefore(unescape(uri), "?")).getName();
					}
					return name;
				}

				@Override
				public boolean equals(Object other) {
					return other == null ? false :
						other == this ? true :
						other instanceof Item ? ((Item)other).uri.equals(uri) :
						other.toString().equals(uri);
				}

				@Override
				public int hashCode() {
					return uri.hashCode();
				}
			}
		}
	}
}