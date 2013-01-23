import java.io.*;
import java.util.zip.CRC32;

public class Quantum implements Serializable
{
    private transient byte[] _serialized = null;
    
    private String _prefix;
    private int _date;
    private int _day;
    private int _hour;
    
    public Quantum()
    {
        _prefix = "prefix";
        _date = 0;
        _day = 1;
        _hour = 11;
    }
    
    public Quantum(String prefix, int date, int day, int hour)
    {
        _prefix = prefix;
        _date = date;
        _day = day;
        _hour = hour;
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
        Quantum tmpResult = (Quantum)tmpObjectInput.readObject();
        tmpObjectInput.close();
        tmpByteInput.close();
        
        _serialized = serialized;
        _prefix = tmpResult._prefix;
        _date = tmpResult._date;
        _day = tmpResult._day;
        _hour = tmpResult._hour;
    }
    
    public long getChecksum() throws IOException
    {
        CRC32 tmpChecksum = new CRC32();
        tmpChecksum.update(serialize());

        return tmpChecksum.getValue();
    }

    public String getPrefix()
    {
        return _prefix;
    }

    public int getDate()
    {
        return _date;
    }

    public int getDay()
    {
        return _day;
    }

    public int getHour()
    {
        return _hour;
    }
}
