import java.util.EventObject;

public class ClientWorkerEventObject extends EventObject
{
    private ClientWorker _source;
    private SuccessOrFail _successOrFail;
    private QuantumResult _result;

    public ClientWorkerEventObject(Object source, SuccessOrFail successOrFail, QuantumResult result) throws IllegalArgumentException
    {
        super(source);
        
        _source = (ClientWorker)source;
        _successOrFail = successOrFail;
        _result = result;
        
        if (_successOrFail == SuccessOrFail.Success && _result == null)
            throw new IllegalArgumentException("The result can't be null while the type is Success.");
    }

    public ClientWorker getSource()
    {
        return _source;
    }

    public SuccessOrFail getSuccessOrFail()
    {
        return _successOrFail;
    }

    public QuantumResult getResult()
    {
        return _result;
    }
}
