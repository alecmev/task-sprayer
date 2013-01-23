import com.google.common.base.Stopwatch;

import java.util.HashMap;

public class TimeProfiler
{
    private static final TimeProfiler _instance = new TimeProfiler();
    
    private final Stopwatch _stopwatch = new Stopwatch();
    private final HashMap<String, Long> _milestones = new HashMap<String, Long>();
    
    private TimeProfiler()
    {
        _stopwatch.start();
    }

    public static void set(String key)
    {
        synchronized (_instance)
        {
            _instance._milestones.put(key, _instance._stopwatch.elapsedMillis());
        }
    }

    public static boolean exists(String key)
    {
        synchronized (_instance)
        {
            return _instance._milestones.containsKey(key);
        }
    }

    public static long get(String key) throws IllegalArgumentException
    {
        synchronized (_instance)
        {
            if (exists(key))
                return _instance._stopwatch.elapsedMillis() - _instance._milestones.get(key);
            else
                throw new IllegalArgumentException("No milestone with such key.");
        }
    }

    public static void remove(String key)
    {
        synchronized (_instance)
        {
            if (exists(key))
                _instance._milestones.remove(key);
            else
                throw new IllegalArgumentException("No milestone with such key.");
        }
    }
}
