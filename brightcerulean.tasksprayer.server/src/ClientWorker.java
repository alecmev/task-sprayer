import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ClientWorker implements Runnable
{
    private Socket _socket;
    private InputStream _inputStream;
    private OutputStream _outputStream;
    private TaskQuantum _taskQuantum = null;
    private boolean _taskQuantumUploaded = false;

    private volatile boolean _stop = false;

    public ClientWorker(Socket socket) throws IOException
    {
        System.out.println("Client connected");
        _socket = socket;
        _inputStream = _socket.getInputStream();
        _outputStream = _socket.getOutputStream();
    }

    public void run()
    {
        ClientListener tmpClientListener = Main.getClientListener();
        TaskHub tmpTaskHub = Main.getTaskHub();
        String tmpTimeProfilerId = "client-worker-" + Thread.currentThread().getId();
        
        try
        {
            while (!_stop)
            {
                synchronized (this)
                {
                    if (_taskQuantum != null)
                    {
                        if (!_taskQuantumUploaded)
                        {
                            byte[] tmpTaskQuantumSerialized = _taskQuantum.getQuantum().serialize();
                            _outputStream.write(ByteBuffer.allocate(12 + tmpTaskQuantumSerialized.length)
                                            .putLong(_taskQuantum.getQuantum().getChecksum())
                                            .putInt(tmpTaskQuantumSerialized.length)
                                            .put(tmpTaskQuantumSerialized).array());
                            TimeProfiler.set(tmpTimeProfilerId);
                            _taskQuantumUploaded = true;
                        }
                        
                        if (_inputStream.available() > 0)
                        {
                            SuccessOrFail tmpSuccessOrFail = SuccessOrFail.values()[_inputStream.read()];
                            
                            if (tmpSuccessOrFail == SuccessOrFail.Success)
                            {
                                try { Thread.sleep(10); } catch (Exception e) { }

                                if (_inputStream.available() < 12)
                                    throw new Exception();
                                
                                byte[] tmpHeader = new byte[12];
                                _inputStream.read(tmpHeader, 0, 12);
                                ByteBuffer tmpByteBuffer = ByteBuffer.wrap(tmpHeader);
                                long tmpChecksum = tmpByteBuffer.getLong();
                                int tmpLength = tmpByteBuffer.getInt();
                            
                                try { Thread.sleep(10); } catch (Exception e) { }

                                if (_inputStream.available() != tmpLength)
                                    throw new Exception();
                                
                                byte[] tmpData = new byte[tmpLength];
                                _inputStream.read(tmpData);
                                QuantumResult tmpQuantumResult = new QuantumResult();
                                tmpQuantumResult.deserialize(tmpData);
                                
                                if (tmpQuantumResult.getChecksum() != tmpChecksum)
                                    throw new Exception();
                                
                                tmpTaskHub.solvedTaskQuantum(_taskQuantum, tmpQuantumResult);
                                TimeProfiler.remove(tmpTimeProfilerId);
                            }
                            else
                            {
                                tmpTaskHub.solvedTaskQuantum(_taskQuantum, null);
                            }

                            tmpClientListener.returnClientWorker(this);
                        }
                        
                        if (TimeProfiler.exists(tmpTimeProfilerId) && (TimeProfiler.get(tmpTimeProfilerId) >= 360000)) // 6 minutes
                            throw new Exception();
                    }
                }

                try { Thread.sleep(1); } catch (Exception e) { }
            }
        }
        catch (Exception e)
        {
            if (_taskQuantum != null)
                tmpTaskHub.returnTaskQuantum(_taskQuantum);

            tmpClientListener.removeClientWorker(this);
            
            if (TimeProfiler.exists(tmpTimeProfilerId))
                TimeProfiler.remove(tmpTimeProfilerId);
        }

        try { _socket.close(); } catch (Exception e) { }
    }

    public void stop()
    {
        _stop = true;
    }
    
    public synchronized void assignTaskQuantum(TaskQuantum taskQuantum)
    {
        _taskQuantum = taskQuantum;
        _taskQuantumUploaded = false;
    }
}
