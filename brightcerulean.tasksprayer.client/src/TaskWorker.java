import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Random;

public class TaskWorker implements Runnable
{
    private ServerWorker _serverWorker;
    private Quantum _quantum;
    
    private volatile boolean _stop = false;
    
    public TaskWorker(ServerWorker serverWorker, Quantum quantum)
    {
        _serverWorker = serverWorker;
        _quantum = quantum;
    }
    
    public void run()
    {
        Process tmpProcess = null;
        
        try
        {
            // SAMPLE TASK
            
            Thread.sleep(50);
            Random tmpRandom = new Random(Thread.currentThread().getId());
            if (tmpRandom.nextInt(8000) != 4000) throw new Exception();
            _serverWorker.solvedQuantum(new QuantumResult(
                    tmpRandom.nextLong(),
                    tmpRandom.nextLong(),
                    tmpRandom.nextLong(),
                    tmpRandom.nextLong()));

            // SAMPLE TASK
            
            // Uncomment the block below for a real solution
            
            /*tmpProcess = Runtime.getRuntime().exec("./calc"); // MODIFY THIS, use _qunatum's data (the definition of task quantum can be found in tasksprayer.shared.Quantum)
            InputStream tmpInput = tmpProcess.getInputStream();
            
            while (!_stop)
            {
                if (tmpInput.available() >= 32) // four 64-bit integers
                {
                    byte[] tmpData = new byte[32];
                    tmpInput.read(tmpData, 0, 32);
                    ByteBuffer tmpByteBuffer = ByteBuffer.wrap(tmpData);
                    _serverWorker.solvedQuantum(new QuantumResult(
                            tmpByteBuffer.getLong(),
                            tmpByteBuffer.getLong(),
                            tmpByteBuffer.getLong(),
                            tmpByteBuffer.getLong()));
                    break;
                }
                
                if (TimeProfiler.get("task-worker") >= 300000) // 5 minutes
                    throw new Exception();

                Thread.sleep(1000);
            }*/
        }
        catch (Exception e)
        {
            _serverWorker.solvedQuantum(null);
        }

        if (tmpProcess != null)
            tmpProcess.destroy();
    }
}
