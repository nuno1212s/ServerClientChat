import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChatServer {

    private static ChatServer ins;

    public static ChatServer getInstance() {
        return ins;
    }

    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // Decoder for incoming text -- assume UTF-8
    static public final Charset charset = StandardCharsets.UTF_8;
    static private final CharsetDecoder decoder = charset.newDecoder();

    private Selector selector;

    private List<ClientHandler> clients;

    private Map<String, List<ClientHandler>> rooms;

    public ChatServer(int port) {
        ins = this;
        this.clients = new LinkedList<>();
        this.rooms = new HashMap<>();

        try {
            acceptConnections(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptConnections(int port) throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();

        ssc.configureBlocking(false);

        ServerSocket socket = ssc.socket();
        InetSocketAddress address = new InetSocketAddress(port);

        socket.bind(address);

        this.selector = Selector.open();

        ssc.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Listening on port: " + port);

        while (true) {

            int num = selector.select();

            if (num == 0) {
                continue;
            }

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();

            while (iterator.hasNext()) {

                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {

                    // It's an incoming connection.  Register this socket with
                    // the Selector so we can listen for input on it
                    Socket s = socket.accept();
                    System.out.println("Got connection from " + s);

                    // Make sure to make it non-blocking, so we can use a selector
                    // on it.
                    SocketChannel sc = s.getChannel();
                    sc.configureBlocking(false);

                    // Register it with the selector, for reading
                    sc.register(selector, SelectionKey.OP_READ);

                    ClientHandler clientHandler = new ClientHandler(sc);

                    this.clients.add(clientHandler);
                } else if (key.isReadable()) {

                    SocketChannel channel = (SocketChannel) key.channel();

                    boolean ok = processInput(channel, getHandlerForSocket(channel));

                    if (!ok) {
                        key.cancel();

                        deleteClient(getHandlerForSocket(channel));
                    }

                }

            }

            keys.clear();
        }
    }

    private ClientHandler getHandlerForSocket(SocketChannel socket) {

        for (ClientHandler client : this.clients) {
            if (client.getSocket().equals(socket)) return client;
        }

        return null;
    }

    private boolean processInput(SocketChannel channel, ClientHandler handler) throws IOException {
        buffer.clear();

        channel.read(buffer);

        buffer.flip();

        if (buffer.limit() == 0) {
            return false;
        }

        try {
            String message = decoder.decode(buffer).toString();

            handler.receive(message);
        } catch (CharacterCodingException e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Change a users chat room
     *
     * @param handler      The user to change chat room
     * @param previousChat The previous chat room
     * @param newChat      The new chat room
     */
    public void changeChatRoom(ClientHandler handler, String previousChat, String newChat) {

        if (previousChat != null && this.rooms.containsKey(previousChat)) {
            List<ClientHandler> remove = this.rooms.get(previousChat);

            remove.remove(handler);

            sendMessageToRoom(previousChat, String.format(ClientHandler.LEFT, handler.getName()));

        }

        if (newChat != null) {
            List<ClientHandler> clients = this.rooms.getOrDefault(newChat, new LinkedList<>());

            handler.sendMessage(ClientHandler.OK);
            sendMessageToRoom(newChat, String.format(ClientHandler.JOINED, handler.getName()));

            clients.add(handler);

            this.rooms.put(newChat, clients);
        }

    }

    /**
     * Delete a client from the client list
     *
     * @param clientHandler
     */
    public void deleteClient(ClientHandler clientHandler) {

        clientHandler.sendMessage(ClientHandler.BYE);

        this.clients.remove(clientHandler);

        changeChatRoom(clientHandler, clientHandler.getCurrentChatRoom(), null);

        try {
            clientHandler.getSocket().close();

            System.out.println("Closed connection " + clientHandler.getSocket().socket());
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (SelectionKey key : this.selector.keys()) {
            if (key.channel().equals(clientHandler.getSocket())) {
                key.cancel();
                break;
            }
        }

    }

    /**
     * Send a message to all the connected clients
     *
     * @param message The message to send
     */
    public void sendGlobalMessage(String message) {

        for (ClientHandler client : this.clients) {
            client.sendMessage(message);
        }

    }

    /**
     * Send a message to all the clients connected to a room
     *
     * @param room    The room they are connected to
     * @param message The message to send
     */
    public void sendMessageToRoom(String room, String message) {

        this.rooms.getOrDefault(room, new LinkedList<>())
                .forEach((client) -> client.sendMessage(message));

    }

    /**
     * Check if the username is taken by any of the online users
     *
     * @param name The name to check for
     * @return
     */
    public boolean isUsernameTaken(String name) {
        for (ClientHandler client : this.clients) {

            if (client.getState() == ClientHandler.State.OUTSIDE || client.getState() == ClientHandler.State.INSIDE) {
                if (client.getName().equalsIgnoreCase(name)) {
                    return true;
                }
            }

        }

        return false;
    }

    /**
     * Accept a user changing nick names
     *
     * @param clientHandler The user that is changing
     * @param oldName       The old name
     * @param newName       The new name
     */
    public void acceptUserName(ClientHandler clientHandler, String oldName, String newName) {

        if (clientHandler.getState() == ClientHandler.State.INSIDE) {
            sendMessageToRoom(clientHandler.getCurrentChatRoom(), String.format(ClientHandler.NEW_NICK, oldName, newName));
        }

    }

    /**
     * Accept a private message sent by a user
     *
     * @param sender The user that sent the message
     * @param dest   The destination of the message
     * @param msg    The message
     */
    public void acceptPrivateMessage(ClientHandler sender, String dest, String msg) {

        boolean found = false;

        for (ClientHandler client : this.clients) {
            if (client.getName().equalsIgnoreCase(dest)) {
                found = true;
                client.sendMessage(String.format(ClientHandler.PRIVATE, sender.getName(), msg));
                break;
            }
        }

        if (found) {
            sender.sendMessage(ClientHandler.OK);
        } else {
            sender.sendMessage(ClientHandler.ERROR);
        }

    }

    public void acceptMessage(ClientHandler sender, String message) {
        if (sender.getState() == ClientHandler.State.INSIDE) {
            sendMessageToRoom(sender.getCurrentChatRoom(), String.format(ClientHandler.MESSAGE, sender.getName(), message));
        } else {
            sender.sendMessage(ClientHandler.ERROR);
        }
    }

    public static void main(String[] args) {
        new ChatServer(Integer.parseInt(args[0]));
    }

}
