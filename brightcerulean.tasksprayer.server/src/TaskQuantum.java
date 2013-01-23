public class TaskQuantum
{
    private Task _task;
    private Quantum _quantum;
    
    public TaskQuantum(Task task, String prefix, int date, int day, int hour)
    {
        _task = task;
        _quantum = new Quantum(prefix, date, day, hour);
    }
    
    public Task getTask()
    {
        return _task;
    }
    
    public Quantum getQuantum()
    {
        return _quantum;
    }
}
