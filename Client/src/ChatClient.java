import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;

public class ChatClient {

    private static final ByteBuffer buffer = ByteBuffer.allocate(16384);

    private static final Charset charset = StandardCharsets.UTF_8;

    private static final CharsetDecoder decoder = charset.newDecoder();

    private static final Pattern pattern_message = Pattern.compile("(MESSAGE )([a-zA-Z0-9_]+) (.+)"),
            pattern_priv = Pattern.compile("(PRIVATE )([a-zA-Z0-9_]+) (.+)"),
            pattern_joined = Pattern.compile("(JOINED )([a-zA-Z0-9_]+)"),
            pattern_left = Pattern.compile("(LEFT )([a-zA-Z0-9_]+)"),
            pattern_new_nick = Pattern.compile("(NEWNICK )([a-zA-Z0-9_]+) ([a-zA-Z0-9_]+)"),
            pattern_bye = Pattern.compile("BYE"),
            pattern_success = Pattern.compile("OK");

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    private SocketChannel socket;

    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {

        socket = SocketChannel.open(new InetSocketAddress(server, port));

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.submit(this::run);

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);

        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocus();
            }

            @Override
            public void windowClosed(WindowEvent windowEvent) {
                try {
                    newMessage("/bye");

                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {

        message += "\n";

        if (message.startsWith("/"))
            printMessage(message);

        try {
            socket.write(ByteBuffer.wrap(message.getBytes(charset)));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // Método principal do objecto
    public void run() {
        // PREENCHER AQUI

        while (true) {
            try {
                buffer.clear();

                socket.read(buffer);

                buffer.flip();

                if (buffer.limit() == 0) {
                    continue;
                }

                try {

                    String message = decoder.decode(buffer).toString();

                    buffer.clear();

                    if (!handleReceiveResponse(message))
                        break;

                } catch (CharacterCodingException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Handle the messages that have been
     * @param message
     */
    private boolean handleReceiveResponse(String message) {

        Matcher message_matcher = pattern_message.matcher(message),
                priv_matcher = pattern_priv.matcher(message),
                joined_matcher = pattern_joined.matcher(message),
                left_matcher = pattern_left.matcher(message),
                new_nick_matcher = pattern_new_nick.matcher(message);

        if (message_matcher.find()) {
            printMessage(String.format("%s: %s\n", message_matcher.group(2), message_matcher.group(3)));
        } else if (priv_matcher.find()) {
            printMessage(String.format("Private Message: %s: %s\n", priv_matcher.group(2), priv_matcher.group(3)));
        } else if (joined_matcher.find()) {
            printMessage(String.format("The user %s has joined the chatroom\n", joined_matcher.group(2)));
        } else if (left_matcher.find()) {
            printMessage(String.format("The user %s has left the chatroom\n", left_matcher.group(2)));
        } else if (new_nick_matcher.find()) {
            printMessage(String.format("The user %s has changed to the nick %s\n", new_nick_matcher.group(2), new_nick_matcher.group(3)));
        } else if (pattern_success.matcher(message).find()) {
            printMessage("Successful\n");
        } else if (pattern_bye.matcher(message).find()) {

            printMessage("Connection closed\n");

            return false;

        } else {
            printMessage(message);
        }

        return true;
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
