public class Main
{
    private static ClientListener _clientListener = null;
    private static TaskHub _taskHub = null;

    public static ClientListener getClientListener()
    {
        return _clientListener;
    }

    public static TaskHub getTaskHub()
    {
        return _taskHub;
    }

    private static boolean _stop = false;
    
    public static void main(String[] args) throws Exception
    {
        _clientListener = new ClientListener(); // responsible for accepting all clients and for organizing them in two collections - free and busy
        _taskHub = new TaskHub(); // responsible for [re-]loading tasks from the task file and for separating them in task quantums

        (new Thread(_clientListener)).start();
        (new Thread(_taskHub)).start();
        
        while (!_stop)
        {
            if (System.in.available() > 0)
            {
                switch (System.in.read())
                {
                    case 'r':
                        
                        System.out.println("Reloading tasks...");
                        _taskHub.loadTasksSafe();
                        
                        break;
                    case '|': // _preferred_ way to stop the server
                        
                        System.out.println("Quitting...");
                        _stop = true;

                        break;
                    case '\n':
                        break;
                    default:

                        System.out.println("What?");
                        
                        break;
                }
            }
            else
                try { Thread.sleep(1); } catch (Exception e1) { }
        }

        _clientListener.stop();
        _taskHub.stop();
        
        try { Thread.sleep(100); } catch (Exception e1) { }
    }
}
