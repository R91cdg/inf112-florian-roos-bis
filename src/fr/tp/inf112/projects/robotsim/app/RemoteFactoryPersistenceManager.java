package fr.tp.inf112.projects.robotsim.app;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasChooser;
import fr.tp.inf112.projects.canvas.model.impl.AbstractCanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Factory;

public class RemoteFactoryPersistenceManager extends AbstractCanvasPersistenceManager {

    private String serverAddress = "localhost";
    private int serverPort = 51100;

    public RemoteFactoryPersistenceManager(CanvasChooser canvasChooser) {
        super(canvasChooser);
    }

    @Override
    public void persist(Canvas canvasModel) {
        if (canvasModel instanceof Factory) {
            try (
                Socket socket = new Socket(serverAddress, serverPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ) {
                out.writeObject(canvasModel);
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Canvas read(String canvasId) {
        try (
            Socket socket = new Socket(serverAddress, serverPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {
            out.writeObject(canvasId);
            out.flush();
            return (Canvas) in.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean delete(Canvas canvasModel) {
        // Not implemented as per instructions
        return false;
    }
}
