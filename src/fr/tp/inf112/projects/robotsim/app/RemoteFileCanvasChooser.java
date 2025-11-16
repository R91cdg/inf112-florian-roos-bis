package fr.tp.inf112.projects.robotsim.app;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import javax.swing.JOptionPane;

import fr.tp.inf112.projects.canvas.view.FileCanvasChooser;

public class RemoteFileCanvasChooser extends FileCanvasChooser {

    private String serverAddress = "localhost";
    private int serverPort = 51100;

    public RemoteFileCanvasChooser(String fileExtension, String fileDescription) {
        super(fileExtension, fileDescription);
    }

    @Override
    public String browseCanvases(boolean open) {
        if (open) {
            try (
                Socket socket = new Socket(serverAddress, serverPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ) {
                out.writeObject("LIST_FILES");
                out.flush();
                String[] files = (String[]) in.readObject();
                
                return (String) JOptionPane.showInputDialog(
                    null,
                    "Choose a factory to open:",
                    "Open Factory",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    files,
                    files.length > 0 ? files[0] : null
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return JOptionPane.showInputDialog("Enter a name for the new factory:");
        }
    }
}
