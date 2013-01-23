import java.io.*;
import java.util.zip.CRC32;

public class QuantumResult implements Serializable
{
    private transient byte[] _serialized = null;
    
    private long[] _result;
    
    public QuantumResult()
    {
        _result = new long[] { 0, 0, 0, 0 };
    }
    
    public QuantumResult(long r0, long r1, long r2, long r3)
    {
        _result = new long[] { r0, r1, r2, r3 };
    }

    public byte[] serialize() throws IOException
    {
        if (_serialized == null)
        {
            ByteArrayOutputStream tmpByteOutput = new ByteArrayOutputStream();
            ObjectOutputStream tmpObjectOutput = new ObjectOutputStream(tmpByteOutput);
            tmpObjectOutput.writeObject(this);
            _serialized = tmpByteOutput.toByteArray();
            tmpObjectOutput.close();
            tmpByteOutput.close();
        }

        return _serialized;
    }

    public void deserialize(byte[] serialized) throws IOException, ClassNotFoundException
    {
        ByteArrayInputStream tmpByteInput = new ByteArrayInputStream(serialized);
        ObjectInputStream tmpObjectInput = new ObjectInputStream(tmpByteInput);
        QuantumResult tmpResult = (QuantumResult)tmpObjectInput.readObject();
        tmpObjectInput.close();
        tmpByteInput.close();

        _serialized = serialized;
        _result = tmpResult._result;
    }

    public long getChecksum() throws IOException
    {
        CRC32 tmpChecksum = new CRC32();
        tmpChecksum.update(serialize());

        return tmpChecksum.getValue();
    }

    public long[] getResult()
    {
        return _result;
    }
}
