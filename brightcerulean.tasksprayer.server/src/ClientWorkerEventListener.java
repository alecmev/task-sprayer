import java.util.EventListener;

public interface ClientWorkerEventListener extends EventListener
{
    public void invoke(ClientWorkerEventObject e);
}
