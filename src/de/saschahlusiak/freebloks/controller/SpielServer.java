package de.saschahlusiak.freebloks.controller;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;

import android.util.Log;

import de.saschahlusiak.freebloks.model.Ki;
import de.saschahlusiak.freebloks.model.Player;
import de.saschahlusiak.freebloks.model.Spiel;
import de.saschahlusiak.freebloks.model.Stone;
import de.saschahlusiak.freebloks.model.Turn;
import de.saschahlusiak.freebloks.network.*;

public class SpielServer extends Spielleiter {
	private static final String tag = SpielServer.class.getSimpleName();
	
	Ki m_ki;
	Socket clients[];
	int ki_mode;
	ServerListener listener = null;

	public SpielServer(int size_y, int size_x, int ki_mode) {
		super(size_y, size_x);
		
		this.ki_mode = ki_mode;
		m_ki = new Ki();
		start_new_game();
		clients = new Socket[8];
	}
	
	synchronized public void release() {
		for (Socket s: clients) if (s != null) {
			try {
				s.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			s = null;
		}
		clients = null;
	}

	/**
	 * Gibt Anzahl der Verbundenen Clients zurueck
	 **/
	synchronized int get_num_clients() {
		int n = 0;
		for (Socket s: clients)
			if (s != null)
				n++;
		return n;
	}

	synchronized boolean add_client(Socket s) {
		for (int i=0; i<clients.length; i++)if (clients[i] == null) {
			send_server_msg(String.format("Client %d joined", i));
			
			clients[i] = s;
			send_server_status();
			
			new ClientThread(i).start();
			
			return true;
		}
		/* Sonst Verbindung schliessen */
		try {
			s.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Entfernt den client[index], schliesst seine TCP Verbindung und ersetzt ihn durch einen COMPUTER
	 **/
	synchronized void delete_client(int index, boolean notify) {
		/* Alle Spieler, die der Client belegt hat, werden durch einen PLAYER_COMPUTER ersetzt */
		for (int i = 0; i < PLAYER_MAX; i++)
			if (spieler[i] == index)
				spieler[i] = PLAYER_COMPUTER;
	
		/* Socket zu dem Client schliessen */
		if (clients[index] == null)
			return;
		
		try {
			clients[index].close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		clients[index] = null;
		/* Aktuellen Serverstatus an restliche Clients verbreiten */
		send_server_status();
		if (notify) {
			send_server_msg(String.format("Client %d left\n", index));
		}
		if (get_num_clients() <= 0 && listener != null)
			listener.close();
	}
	
	private class ClientThread extends Thread {
		private final String tag = ClientThread.class.getSimpleName();
		
		int index;
		
		public ClientThread(int index) {
			this.index = index;
		}
		
		@Override
		public void run() {
			Log.d(tag, "start");
			
			do {
				
			} while (handle_client(index));
			
			Log.d(tag, "going down");
		}
	}
	
	synchronized void do_computer_turn() {
		if (m_current_player < 0)
			return;
		if (spieler[m_current_player] != PLAYER_COMPUTER)
			return;

		/* Ermittle CTurn, den die KI jetzt setzen wuerde */
		Turn turn=m_ki.get_ki_turn(this, current_player(), ki_mode);
		if (turn != null) {
			/* Datenstruktur fuellen, die an die Clients geschickt werden soll. */
			NET_SET_STONE data = new NET_SET_STONE();

			data.player = current_player();
			data.stone = turn.m_stone_number;
			data.mirror_count = turn.m_mirror_count;
			data.rotate_count = turn.m_rotate_count;
			data.x = turn.m_x;
			data.y = turn.m_y;
			/* Zug lokal wirklich setzen, wenn Fehlschlag, ist das Spiel wohl nicht mehr synchron */
			if ((is_valid_turn(turn) == Stone.FIELD_DENIED) ||
			   (set_stone(turn) != Stone.FIELD_ALLOWED))
			{
				Log.e(tag, "Game not in sync (2)");
				return;
			}
			/* sonst Spielzug an alle Clients uebermitteln */
			send_all(data);
			/* Sowie den Zug der History anhaengen */
			addHistory(turn);
		}
		/* Naechsten Spieler ermitteln */
		next_player();
		/* Ausgewaehlten aktuellen Spieler an alle Clients schicken */
		send_current_player();
	}

		/**
		 * Verarbeitet eine einzige Nachricht eines bestimmten Clients, von dem Daten anliegen
		 */
		boolean handle_client(int index) {
			/* Lese genau eine Netzwerknachricht des Clients in buffer ein. */
			try {
				NET_HEADER data = Network.read_package(clients[index], true);
				
				if (data == null) {
					delete_client(index, true);
					return false;
				} else {
					process_message(index, data);
					return true;
				}
			} catch (Exception e) {
				e.printStackTrace();
				delete_client(index, true);
				return false;
			}
		}

		/**
		 * Verarbeitet eine von einem Client empfangene Netzwerknachricht
		 **/
		synchronized void process_message(int client, NET_HEADER data) {
			switch(data.msg_type)
			{
				/* Der Client fordert einen lokalen Spieler an */
				case Network.MSG_REQUEST_PLAYER: {
					NET_GRANT_PLAYER msg = new NET_GRANT_PLAYER();
					int n;

					/* Ebenso, wenn das Spiel bereits laeuft */
					if (m_current_player != -1) return;

					/* Wenn alle Spieler vergeben, raus */
					if (num_players()>=PLAYER_MAX)return;
					/* Pick zufaellig einen Spieler raus */
					n = (int)(Math.random() * PLAYER_MAX);
					/* Suche den naechsten, der frei ist (!=PLAYER_COMPUTER) */
					while (spieler[n] != PLAYER_COMPUTER)
						n = (n + 1) % PLAYER_MAX;

					/* Schick eine Nachricht zurueck, der ihm den Spieler zugesteht */
					msg.player = n;
					msg.send(clients[client]);
					/* Speichere socket des Spielers in dem spieler[] Array
					   So werden den Spielern wieder die Clients zugeordnet */
					spieler[n] = client;

					/* Aktuellen Serverstatus an Clients senden */
					send_server_status();
//		 			printf("Client %d requested player (#%d)\n",client,n);
					break;
				}

				/* Ein Client hat einen Stein gesetzt */
				case Network.MSG_SET_STONE: {
					NET_SET_STONE s = (NET_SET_STONE)data;
					/* Den entsprechenden Stein aus den Daten zusammensuchen */
					Stone stone = get_player(s.player).get_stone(s.stone);
					stone.mirror_rotate_to(s.mirror_count,s.rotate_count);

					/* Den Stein lokal setzen */
		 			if ((is_valid_turn(stone, s.player, s.y, s.x) == Stone.FIELD_DENIED) ||
					   (set_stone(stone, s.player,s.y,s.x) == Stone.FIELD_ALLOWED))
					{
						/* Bei Erfolg wird die Nachricht direkt an alle Clients zurueck-
						   geschickt */
						send_all(data);
						/* Zug an History anhaengen */
						addHistory(s.player, stone, s.y, s.x);
						/* Dann wird der naechste Spieler ermittelt*/
						next_player();
					}else{ // Spiel scheint nicht synchron zu sein
						Log.e(tag, "Game seems to be out of sync!");
					}
					/* Aktuellen Spieler den Clients mitteilen */
					send_current_player();
					break;
				}

				/* Ein Client erbittet Spielstart. So sei es! */
				case Network.MSG_START_GAME:
//		 			printf("Request game start\n");
					start_game();
					break;

				/* Eine Chat-Nachricht von einem Client empfangen. */
				case Network.MSG_CHAT:
					/* Setze in der Nachricht die Nummer des Clients, der sie versendet hat. */
					NET_CHAT chat = (NET_CHAT)data;
					chat.client = client;

					/* Zwangsnullterminiere den empfangenen Text. Nur zur Sicherheit. */
//					((NET_CHAT*)data)->text[ntohs(data->data_length)-sizeof(NET_CHAT)-1]='\0';

					/* Schicke leicht modifizierte Chat-Nachricht an alle anderen Clients weiter. */
					send_all(data);

					Log.i(tag, String.format("Client %d: %s", client, chat.text));
					break;
				

				/* Ein Client will eine Zugzuruecknahme */
				case Network.MSG_REQUEST_UNDO: {
					/* Zugzuruecknahme ist nur bei einem Client oder einem Menschlichem 
					   Spieler zulaessig. */
					if (get_num_clients() > 1 && num_players() > 1) {
//		 				printf("Client %d requested undo. IGNORED.\n",client);
						return;
					}
//		 			printf("Client %d requesting undo. ",client);
					NET_UNDO_STONE undo;
					int i=0;
					/* Solange Steine zurueck nehmen, bis keine mehr in der History vorliegen,
					   oder ein menschlicher Spieler wieder dran ist. */
					do
					{
						Turn turn = history.get_last_turn();
						if (turn == null)
							break; // Kein Zug mehr in der History
						i++;
						// "Zug zuruecknehmen" an Clients senden
						send_all(data);
						// Spieler von zurueckgenommenen Stein ist wieder dran
						m_current_player = turn.m_playernumber;
						// Aktuellen Spieler uebermitteln
						send_current_player();
						// Und lokal den Zug zuruecknehmen
						undo_turn(history);
						// Solange Zuege des Computers zurueckgenommen werden
					}while (spieler[m_current_player]==PLAYER_COMPUTER);
//		 			printf("Removed %d turns.\n",i);
					break;
				}
				
				case Network.MSG_REQUEST_HINT: {
					NET_REQUEST_HINT hint = (NET_REQUEST_HINT) data;
					Turn turn=m_ki.get_ki_turn(this, hint.player, Ki.HARD);
					NET_SET_STONE d = new NET_SET_STONE();

					d.player = hint.player;
					d.stone = turn.m_stone_number;
					d.mirror_count = turn.m_mirror_count;
					d.rotate_count = turn.m_rotate_count;
					d.x = turn.m_x;
					d.y = turn.m_y;
					d.send(clients[client]);

					break;
				}

				default: Log.e(tag, "FEHLER: Unbehandelte Netzwerknachricht: #" + data.msg_type);
					break;
			}
		}

		/**
		 * Schicke eine Nachricht an alle verbundenen Clients
		 **/
		synchronized void send_all(NET_HEADER data) {
			for (int i = 0; i < clients.length; i++) if (clients[i] != null)
				if (! data.send(clients[i])) {
					/* Fehler beim Senden, trenne Verbindung zum Client. */
					delete_client(i, true);
				}
		}

		/**
		 * Schicke aktuelle Spielernummer an alle Clients
		 **/
		void send_current_player() {
			NET_CURRENT_PLAYER data = new NET_CURRENT_PLAYER();
			data.player = current_player();
			send_all(data);
		}

		/**
		 * Schicke Serverstatus an alle Clients
		 * (Anzahl Spieler, Anzahl Computerspieler, Anzahl verbundener Clients, Breite und Hoehe)
		 **/
		void send_server_status() {
			NET_SERVER_STATUS status = new NET_SERVER_STATUS();
			int max = PLAYER_MAX;
			status.player = num_players();
			status.computer = max-num_players();
			status.clients = get_num_clients();
			status.width = m_field_size_x;
			status.height = m_field_size_y;
			for (int i = 0; i < Stone.STONE_SIZE_MAX; i++)
				status.stone_numbers[i] = 1;
			status.gamemode = m_gamemode;
			send_all(status);
		}

		/**
		 * Schickt eine Chat-Nachricht an alle Clients, dessen Absender der Server ist
		 **/
		void send_server_msg(String text) {
			/* Speicher fuer NET_CHAT reservieren, wo text reinpasst. */
			NET_CHAT chat=new NET_CHAT(text, -1);
			/* Chat-Nachricht an alle Clients. */
			send_all(chat);
		}

		/**
		 * Starte das eigentliche Spiel
		 **/
		synchronized void start_game() {
			/* Wenn es bereits laeuft, mache nichts */
			if (m_current_player != -1)
				return;
//		 	printf("Starting game\n");

			/* Spiel zuruecksetzen */
			if (history != null)
				history.delete_all_turns();
			start_new_game();
			set_stone_numbers(1, 1, 1, 1, 1);

			/* Startspieler ist immer Spieler 0 (=Blau) */
			m_current_player = 0;
			/* Schicke Server Status, Info ueber Spielstart, sowie aktuellen Spieler los */
			send_server_status();

			send_all(new NET_START_GAME());
			send_current_player();
			
			do_computer_turn();
		}

		/**
		 * Ermittle naechsten Spieler
		 **/
		synchronized void next_player() {
			int i;
			for (i = 0; i < PLAYER_MAX; i++) {
				m_current_player = (m_current_player + 1) % PLAYER_MAX;
				/* Wenn der naechste Spieler in der Reihe noch mindestens einen freien Zug hat, 
				   ist dieser dran. Sonst muss er aussetzen, und der uebernaechste wird probiert. */
				if (get_player(m_current_player).m_number_of_possible_turns > 0) {
//		 			printf("Spieler %d ist dran: Hat %d moegliche Zuege.\n",m_current_player,get_number_of_possible_turns(m_current_player));
					
					do_computer_turn();
					
					return;
				}//else printf("Spieler %d muss aussetzen.\n",m_current_player);
			}
			/* Ist man hier angelangt, hat keiner der PLAYER_MAX Spieler noch freie Zuege.
			   Das Spiel ist vorbei. */
			m_current_player = -1;

			/* Schicke eine MSG_GAME_FINISH Nachricht an die Clients, die ueber das Spielende informiert. */
			send_all(new NET_GAME_FINISH());

			/* Statusmeldungen auf Konsole ausgeben (z.B. fuer dedicated Server) */
			Log.i(tag, "-- Game finished! --");
			for (i = 0; i < PLAYER_MAX; i++) {
				Player player = get_player(i);
				Log.i(tag, String.format("Player %d has %d stones left and %d points",
						i,
						player.m_stone_count,
						-player.m_stone_points_left));
			}
		}


}