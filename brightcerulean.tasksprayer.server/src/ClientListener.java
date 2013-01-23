import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ClientListener implements Runnable
{
    private ServerSocket _serverSocket = null;
    private final ArrayList<ClientWorker> _freeWorkers = new ArrayList<ClientWorker>();
    private final ArrayList<ClientWorker> _busyWorkers = new ArrayList<ClientWorker>();

    private volatile boolean _stop = false;
    
    public ClientListener() throws IOException
    {
        _serverSocket = new ServerSocket();
        _serverSocket.setReuseAddress(true);
        _serverSocket.bind(new InetSocketAddress(8844));
    }
    
    public void run()
    {
        System.out.println("Client listener started");
        
        while (!_stop)
        {
            Socket tmpSocket = null;

            try
            {
                tmpSocket = _serverSocket.accept();
                tmpSocket.setTcpNoDelay(true);

                synchronized (this)
                {
                    ClientWorker tmpWorker = new ClientWorker(tmpSocket);
                    (new Thread(tmpWorker)).start();
                    _freeWorkers.add(tmpWorker);
                }
            }
            catch (Exception e1)
            {
                if (tmpSocket != null)
                    try { tmpSocket.close(); } catch (Exception e2) { }
                
                System.out.println("Client connection failed");
            }
        }

        for (ClientWorker tmpWorker : _busyWorkers)
            tmpWorker.stop();

        for (ClientWorker tmpWorker : _freeWorkers)
            tmpWorker.stop();

        System.out.println("Client listener stopped");
    }

    public void stop()
    {
        _stop = true;

        if (_serverSocket != null)
            try { _serverSocket.close(); } catch (Exception e) { }

        System.out.println("Client listener stopping...");
    }
    
    public synchronized ClientWorker getFreeClientWorker()
    {
        if (_freeWorkers.size() > 0)
        {
            ClientWorker tmpClientWorker = _freeWorkers.remove(0);
            _busyWorkers.add(tmpClientWorker);
            
            return tmpClientWorker;
        }
        
        return null;
    }
    
    public synchronized void returnClientWorker(ClientWorker clientWorker)
    {
        _busyWorkers.remove(clientWorker);
        _freeWorkers.add(clientWorker);
    }
    
    public synchronized void removeClientWorker(ClientWorker clientWorker)
    {
        _freeWorkers.remove(clientWorker);
        _busyWorkers.remove(clientWorker);
    }
}
