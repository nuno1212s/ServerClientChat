import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler {

    private static final Pattern commandMatcher = Pattern.compile("/.*"),
            nickMatcher = Pattern.compile("(/nick )([a-zA-Z0-9_]+)"),
            pmMatcher = Pattern.compile("(/priv )([a-zA-Z0-9_]+) (.+)"),
            joinMatcher = Pattern.compile("(/join )([a-zA-Z0-9_]+)"),
            leaveMatcher = Pattern.compile("/leave"),
            byeMatcher = Pattern.compile("/bye");

    public static final String LEFT = "LEFT %s\n",
            JOINED = "JOINED %s\n",
            MESSAGE = "MESSAGE %s %s\n",
            NEW_NICK = "NEWNICK %s %s\n",
            PRIVATE = "PRIVATE %s %s\n",
            ERROR = "ERROR\n",
            OK = "OK\n",
            BYE = "BYE\n";

    private StringBuffer currentMessage = new StringBuffer();

    private State state;

    private String name, currentChatRoom;

    private SocketChannel socket;

    public ClientHandler(SocketChannel socket) {
        this.socket = socket;

        state = State.INIT;
    }

    private boolean acceptName(String name) {
        if (ChatServer.getInstance().isUsernameTaken(name)) {
            sendMessage(ERROR);
            return false;
        }

        ChatServer.getInstance().acceptUserName(this, this.name, name);

        this.state = this.state == State.INIT ? State.OUTSIDE : this.state;
        this.name = name;

        sendMessage(OK);
        return true;
    }

    private void setCurrentChatRoom(String currentChatRoom) {

        if (this.state == State.INIT) {
            sendMessage(ERROR);
            return;
        }

        if (currentChatRoom == null && this.state != State.INSIDE) {
            sendMessage(ERROR);
            return;
        } else if (currentChatRoom == null) {
            sendMessage(OK);
        }

        this.state = currentChatRoom == null ? State.OUTSIDE : State.INSIDE;

        ChatServer.getInstance().changeChatRoom(this, this.currentChatRoom, (this.currentChatRoom = currentChatRoom));
    }

    private boolean handleMessage(String msg) {
        if (getState() != State.INSIDE) {
            sendMessage(ERROR);
            return false;
        }

        ChatServer.getInstance().acceptMessage(this, msg);

        return true;
    }

    private void handlePrivateMessage(String dest, String msg) {
        ChatServer.getInstance().acceptPrivateMessage(this, dest, msg);
    }

    /**
     * Get the name of this client
     *
     * @return The name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the current chat room
     *
     * @return The current chat room the client is in
     */
    public String getCurrentChatRoom() {
        return this.currentChatRoom;
    }

    /**
     * Get the state of this state
     *
     * @return The state of this client
     */
    public State getState() {
        return this.state;
    }

    /**
     * The channel this client is on
     *
     * @return
     */
    public SocketChannel getSocket() {
        return this.socket;
    }

    /**
     * Handle receiving a message
     *
     * @param receivedMessage The message that has been received
     */
    public void receive(String receivedMessage) {

        if (!receivedMessage.endsWith("\n")) {
            this.currentMessage.append(receivedMessage);
            return;
        }

        this.currentMessage.append(receivedMessage);

        receivedMessage = this.currentMessage.toString();

        this.currentMessage = new StringBuffer();

        String[] split = receivedMessage.split("\n");

        if (split.length > 1) {
            for (String msg : split) {
                receiveFinalMessage(msg);
            }
        } else {
            receivedMessage = receivedMessage.substring(0, receivedMessage.length() - 1);

            receiveFinalMessage(receivedMessage);
        }

    }

    private void receiveFinalMessage(String receivedMessage) {
        System.out.println("Message received from user " + getName() + ": " + receivedMessage);

        //Check if it's a command
        if (commandMatcher.matcher(receivedMessage).find()) {

            Matcher nickMatcher = ClientHandler.nickMatcher.matcher(receivedMessage),
                    privMatcher = ClientHandler.pmMatcher.matcher(receivedMessage),
                    joinMatcher = ClientHandler.joinMatcher.matcher(receivedMessage);

            if (nickMatcher.find()) {
                acceptName(nickMatcher.group(2));
                return;
            } else if (joinMatcher.find()) {
                setCurrentChatRoom(joinMatcher.group(2));
                return;
            } else if (privMatcher.find()) {
                handlePrivateMessage(privMatcher.group(2), privMatcher.group(3));
                return;
            } else if (leaveMatcher.matcher(receivedMessage).find()) {
                setCurrentChatRoom(null);
                return;
            } else if (byeMatcher.matcher(receivedMessage).find()) {
                ChatServer.getInstance().deleteClient(this);
                return;
            }

        }

        handleMessage(escapeBars(receivedMessage));
    }

    private String escapeBars(String msg) {

        if (msg.startsWith("//")) {
            return msg.substring(1);
        }

        return msg;
    }

    /**
     * Send a message to this user
     *
     * @param msg
     */
    public void sendMessage(String msg) {

        System.out.println("Message sent to user " + getName() + ": " + msg);

        try {
            this.socket.write(ByteBuffer.wrap(msg.getBytes(ChatServer.charset)));
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public enum State {
        INIT,
        INSIDE,
        OUTSIDE
    }
}
