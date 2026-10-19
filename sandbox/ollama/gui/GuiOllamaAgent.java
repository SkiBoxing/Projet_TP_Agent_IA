package sandbox.ollama.gui;

import jade.gui.GuiAgent;
import jade.gui.GuiEvent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * a simple window for a Jade GuiAgent with two texts areas to display
 * informations
 *
 * @author emmanuel adam
 * @version 1
 */
public class GuiOllamaAgent extends JFrame implements ActionListener {
    /**
     * code associated to the Quit button
     */
    public static final int QUITCODE = -1;
    /**
     * code associated to the "send to lobby" button
     */
    public static final int SENDQUERY = 1;
    /**
     * string associated to the Quit button
     */
    private static final String QUITCMD = "-1";
    /**
     * string associated to the send of an offer to the seller (BUTTON ACTION)
     */
    private static final String SENDQUERYCMD = "1";


    /**
     * nb of windows created
     */
    static int nb = 0;
    /**
     * Low Text area
     */
    public JTextArea lowTextArea;
    /**
     * Main Text area
     */
    public JTextArea mainTextArea;

    /**
     * */
    private float currentFontSize = 10;
    private static final float MIN_FONT_SIZE = 6;
    private static final float MAX_FONT_SIZE = 72;
    /**
     * monAgent linked to this frame
     */
    GuiAgent myAgent;
    /**
     * no of the window
     */
    int no;

    /**
     * creates a window and displays it in a free space of the screen
     */
    public GuiOllamaAgent() {
        final int preferedWidth = 500;
        final int preferedHeight = 300;
        no = nb++;

        final Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;
        int x = (no * preferedWidth) % screenWidth;
        int y = (((no * preferedWidth) / screenWidth) * preferedHeight) % screenHeight;

        setBounds(x, y, preferedWidth, preferedHeight);
        buildGui();
        setVisible(true);
    }

    public GuiOllamaAgent(GuiAgent agent) {
        this();
        myAgent = agent;
        setTitle(myAgent.getLocalName());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    /**
     * build the gui : a text area in the center of the window, with scroll bars
     */
    private void buildGui() {
        getContentPane().setLayout(new BorderLayout());
        mainTextArea = new JTextArea();
        mainTextArea.setRows(5);
        JScrollPane jScrollPane = new JScrollPane(mainTextArea);
        getContentPane().add(BorderLayout.CENTER, jScrollPane);
        lowTextArea = new JTextArea(3,1);
        jScrollPane = new JScrollPane(lowTextArea);
        getContentPane().add(BorderLayout.SOUTH, jScrollPane);

        JPanel jpanel = new JPanel();
        jpanel.setLayout(new GridLayout(0, 3));
        // (just add columns to add button, or other thing...
        JButton button = new JButton("--- QUIT ---");
        button.addActionListener(this);
        button.setActionCommand(QUITCMD);
        jpanel.add(button);
        button = new JButton("SEND QUERY");
        button.addActionListener(this);
        button.setActionCommand(SENDQUERYCMD);
        jpanel.add(button);

        // Ajouter les raccourcis clavier Ctrl + et Ctrl -
        setupKeyBindings();

        // Ajouter le support de la molette de souris avec Ctrl
        setupMouseWheelZoom();
        getContentPane().add(BorderLayout.NORTH, jpanel);
    }

    /**
     * add a string to the main text area
     */
    public void println(final String chaine) {
        String texte = mainTextArea.getText();
        texte = texte + chaine + "\n";
        mainTextArea.setText(texte);
        mainTextArea.setCaretPosition(texte.length());
    }

    /**
     * add a string to a text area  (main parameter is no more used)
     * @param chaine text to add
     * @param main if true text is added to the main text area, if false, text is set in the small text area
     */
    public void println(final String chaine, final boolean main) {
        if(main)println(chaine);
        else {
            lowTextArea.setText(chaine);
        }
    }
    private void setupKeyBindings() {
        InputMap inputMap = mainTextArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = mainTextArea.getActionMap();

        // Ctrl + (plus)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK), "zoomIn");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "zoomIn");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), "zoomIn");

        // Ctrl - (moins)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "zoomOut");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), "zoomOut");

        // Ctrl 0 pour réinitialiser
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "resetZoom");

        actionMap.put("zoomIn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoom(1.1f);
            }
        });

        actionMap.put("zoomOut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoom(0.9f);
            }
        });

        actionMap.put("resetZoom", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentFontSize = 14f;
                updateFont();
            }
        });
    }

    private void setupMouseWheelZoom() {
        mainTextArea.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    e.consume(); // Empêche le défilement normal
                    if (e.getWheelRotation() < 0) {
                        zoom(1.1f); // Zoom avant
                    } else {
                        zoom(0.9f); // Zoom arrière
                    }
                }
            }
        });
    }

    private void zoom(float factor) {
        currentFontSize *= factor;
        currentFontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, currentFontSize));
        updateFont();
    }

    private void updateFont() {
        Font currentFont = mainTextArea.getFont();
        mainTextArea.setFont(currentFont.deriveFont(currentFontSize));
    }
    /**
     * reaction to the button event and communication with the agent
     */
    @Override
    public void actionPerformed(ActionEvent evt) {
        final String source = evt.getActionCommand();
        if (source.equals(QUITCMD) || source.equals(SENDQUERYCMD) ) {
            GuiEvent ev = new GuiEvent(this, Integer.parseInt(source));
            myAgent.postGuiEvent(ev);
        }
    }

}
