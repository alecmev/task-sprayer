public class Main
{
    public static void main(String[] args) throws Exception
    {
        (new Thread(new ServerWorker())).start(); // responsible for... everything
    }
}
