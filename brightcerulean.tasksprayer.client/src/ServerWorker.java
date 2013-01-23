import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ServerWorker implements Runnable
{
    private Socket _socket;
    private InputStream _inputStream;
    private OutputStream _outputStream;
    private QuantumResult _quantumResult = null;
    private boolean _quantumReceived = false;
    private boolean _quantumFailed = false;
    
    private volatile boolean _stop = false;
    
    public void run()
    {
        String tmpTimeProfilerId = "server-worker";
        TaskWorker tmpTaskWorker;
        
        while (!_stop)
        {
            try
            {
                System.out.println("Connecting to the server...");
                _socket = new Socket("127.0.0.1", 8844);
                _inputStream = _socket.getInputStream();
                _outputStream = _socket.getOutputStream();
                TimeProfiler.set(tmpTimeProfilerId);
                System.out.println("Success!");
                _quantumResult = null;
                _quantumReceived = false;
                _quantumFailed = false;
                
                while (!_stop)
                {
                    synchronized (this)
                    {
                        if (_quantumResult != null)
                        {
                            byte[] tmpQuantumResultSerialized = _quantumResult.serialize();
                            _outputStream.write(ByteBuffer.allocate(13 + tmpQuantumResultSerialized.length)
                                            .put((byte)SuccessOrFail.Success.ordinal())
                                            .putLong(_quantumResult.getChecksum())
                                            .putInt(tmpQuantumResultSerialized.length)
                                            .put(tmpQuantumResultSerialized).array());
                            _quantumResult = null;
                            _quantumReceived = false;
                            TimeProfiler.set(tmpTimeProfilerId);
                        }
                        
                        if (_quantumFailed)
                        {
                            _outputStream.write(SuccessOrFail.Fail.ordinal());
                            _quantumResult = null;
                            _quantumReceived = false;
                            _quantumFailed = false;
                            TimeProfiler.set(tmpTimeProfilerId);
                        }

                        if (!_quantumReceived && _inputStream.available() >= 12)
                        {
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
                            Quantum tmpQuantum = new Quantum();
                            tmpQuantum.deserialize(tmpData);

                            if (tmpQuantum.getChecksum() != tmpChecksum)
                                throw new Exception();

                            _quantumReceived = true;
                            tmpTaskWorker = new TaskWorker(this, tmpQuantum); // this is where the task quantum's execution actually starts
                            (new Thread(tmpTaskWorker)).start();
                            TimeProfiler.set(tmpTimeProfilerId);
                        }

                        if (TimeProfiler.get(tmpTimeProfilerId) >= 300000) // 5 minutes
                            throw new Exception();
                    }
                }
            }
            catch (Exception e)
            {
                System.out.println("Fail!");
            }

            try { _socket.close(); } catch (Exception e) { }
            
            System.out.println("Disconnected from the server");
        }
    }
    
    public void stop()
    {
        _stop = true;
    }
    
    public synchronized void solvedQuantum(QuantumResult quantumResult)
    {
        if (quantumResult == null)
            _quantumFailed = true;
        else
            _quantumResult = quantumResult;
    }
}
