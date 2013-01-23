import java.util.ArrayList;

public class Task
{
    private int _priority;
    private String _prefix;
    private int _dateFrom, _dateTo, _dayFrom, _dayTo, _hourFrom, _hourTo;
    private int _dateCurrent, _dayCurrent, _hourCurrent, _hourMiddle, _hourStep;
    private long _hoursTotal, _hoursSolved;
    private boolean _noQuantums = false, _solved = false;
    
    public Task(int priority, String prefix, int dateFrom, int dateTo, int dayFrom, int dayTo, int hourFrom, int hourTo)
    {
        setPriority(priority);
        _prefix = prefix;
        
        _dateFrom = dateFrom;
        _dateCurrent = dateFrom;
        _dateTo = dateTo;
        
        _dayFrom = dayFrom;
        _dayCurrent = dayFrom;
        _dayTo = dayTo;
        
        _hourFrom = hourFrom;
        _hourTo = hourTo;
        _hourMiddle = (int)Math.floor((hourFrom + hourTo) / 2d);
        _hourCurrent = _hourMiddle;
        _hourStep = 1;
        
        _hoursTotal = (_dateTo - _dateFrom + 1) * (_dayTo - _dayFrom + 1) * (_hourTo - _hourFrom + 1);
        _hoursSolved = 0;
    }
    
    public synchronized ArrayList<TaskQuantum> getNext() throws Exception
    {
        if (!_solved && !_noQuantums)
        {
            ArrayList<TaskQuantum> tmpTaskQuantums = new ArrayList<TaskQuantum>();
            
            for (int i = 0; i < _priority && !_noQuantums; ++i)
            {
                tmpTaskQuantums.add(new TaskQuantum(this, _prefix, _dateCurrent, _dayCurrent, _hourCurrent));

                _hourCurrent += _hourStep;
                _hourStep += Math.signum(_hourStep) * 1;
                _hourStep *= -1;

                if (_hourCurrent < _hourFrom || _hourCurrent > _hourTo)
                {
                    _hourCurrent = _hourMiddle;
                    _hourStep = 1;

                    if (++_dayCurrent > _dayTo)
                    {
                        _dayCurrent = _dayFrom;

                        if (++_dateCurrent > _dateTo)
                        {
                            _dateCurrent = _dateFrom;
                            _noQuantums = true;
                        }
                    }
                }
            }
            
            return tmpTaskQuantums;
        }
        else
            return null;
    }

    public synchronized int getPriority()
    {
        return _priority;
    }

    public synchronized void setPriority(int priority)
    {
        if (priority < 0)
            throw new IllegalArgumentException("Priority can't be less than zero.");
        
        _priority = priority;
        _noQuantums = _priority == 0;
    }

    public String getPrefix()
    {
        return _prefix;
    }

    public int getDateFrom()
    {
        return _dateFrom;
    }

    public int getDateTo()
    {
        return _dateTo;
    }

    public int getDayFrom()
    {
        return _dayFrom;
    }

    public int getDayTo()
    {
        return _dayTo;
    }

    public int getHourFrom()
    {
        return _hourFrom;
    }

    public int getHourTo()
    {
        return _hourTo;
    }
    
    public long getHoursSolved()
    {
        return _hoursSolved;
    }
    
    public synchronized boolean hourSolved()
    {
        return ++_hoursSolved >= _hoursTotal;
    }
    
    public synchronized void solved()
    {
        _solved = true;
    }
}
