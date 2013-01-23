import com.google.common.collect.HashBiMap;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskHub implements Runnable
{
    private final Pattern _IDPattern = Pattern.compile("#\\s?(\\d+)");
    private final Pattern _priorityPattern = Pattern.compile("!\\s?(\\d+)");
    private final Pattern _prefixPattern = Pattern.compile("@\\s?([\\w\\-\\.]+)");
    private final Pattern _datePattern = Pattern.compile("\\$DT\\s?(\\d+)[\\-\\s]+(\\d+)");
    private final Pattern _dayPattern = Pattern.compile("\\$DY\\s?(\\d+)[\\-\\s]+(\\d+)");
    private final Pattern _hourPattern = Pattern.compile("\\$HR\\s?(\\d+)[\\-\\s]+(\\d+)");
    
    private final HashBiMap<Integer, Task> _taskIndex = HashBiMap.create();
    private ArrayList<Task> _tasks;
    private int _taskCurrent = 0;
    private boolean _noQuantums = true;
    private final ConcurrentLinkedQueue<TaskQuantum> _taskQuantums = new ConcurrentLinkedQueue<TaskQuantum>();
    
    private volatile boolean _stop = false;

    public void run()
    {
        ClientListener tmpClientListener = Main.getClientListener();
        ClientWorker tmpClientWorker;
        loadTasksSafe();
        
        while (!_stop)
        {
            try
            {
                if (!_noQuantums)
                {
                    if ((tmpClientWorker = tmpClientListener.getFreeClientWorker()) != null)
                        tmpClientWorker.assignTaskQuantum(getTaskQuantum());
                    else
                        Thread.sleep(1);
                }
                else
                    Thread.sleep(1);
            }
            catch (Exception e)
            {
            }
        }
    }
    
    public void stop()
    {
        _stop = true;
    }

    public synchronized void loadTasksSafe()
    {
        try
        {
            loadTasks();
        }
        catch (Exception e0)
        {
            System.out.println("Task loading failed");
            try { Thread.sleep(1); } catch (Exception e1) { }
        }
    }
    
    private synchronized TaskQuantum getTaskQuantum() throws Exception
    {
        if (!_noQuantums)
        {
            if (_taskQuantums.isEmpty())
            {
                int tmpTasksSize = _tasks.size();
                
                if (_taskCurrent >= tmpTasksSize)
                    _taskCurrent = 0;
                
                int tmpTaskCurrent = _taskCurrent;
                ArrayList<TaskQuantum> tmpTaskQuantums;
                
                do
                {
                    if ((tmpTaskQuantums = _tasks.get(_taskCurrent++).getNext()) != null)
                    {
                        _taskQuantums.addAll(tmpTaskQuantums);
                        break;
                    }
                    
                    if (_taskCurrent >= tmpTasksSize)
                        _taskCurrent = 0;
                }
                while (_taskCurrent != tmpTaskCurrent);

                if (tmpTaskQuantums == null)
                {
                    _noQuantums = true;
                    _taskCurrent = 0;
                }
            }
            
            return _taskQuantums.poll();
        }
        else
            return null;
    }
    
    public synchronized void returnTaskQuantum(TaskQuantum taskQuantum)
    {
        _taskQuantums.add(taskQuantum);
        _noQuantums = false;
    }
    
    public synchronized void solvedTaskQuantum(TaskQuantum taskQuantum, QuantumResult quantumResult)
    {
        Task tmpTask = taskQuantum.getTask();
        int tmpTaskID = _taskIndex.inverse().get(tmpTask);
        
        try
        {
            if (quantumResult == null)
            {
                if (tmpTask.hourSolved())
                {
                    System.out.println("Task's #" + tmpTaskID + " result: FAIL");
                    (new File("./result-" + tmpTaskID + "-FAIL")).createNewFile();
                }
                else
                    System.out.println("Task #" + tmpTaskID + ": " + tmpTask.getHoursSolved());
            }
            else
            {
                tmpTask.solved();
                long[] tmpResult = quantumResult.getResult();
                
                System.out.println("Task's #" + tmpTaskID + " result: SUCCESS\n" + tmpResult[0] + "\n" + tmpResult[1] + "\n" + tmpResult[2] + "\n" + tmpResult[3]);
                
                FileOutputStream tmpOutput = new FileOutputStream("./result-" + tmpTaskID + "-SUCCESS");
                OutputStreamWriter tmpOutputWriter = new OutputStreamWriter(tmpOutput);
                
                tmpOutputWriter.write(tmpResult[0] + "\n" + tmpResult[1] + "\n" + tmpResult[2] + "\n" + tmpResult[3] + "\n");
                tmpOutputWriter.close();
                tmpOutput.close();
                
                ArrayList<TaskQuantum> tmpTaskQuantumOrphans = new ArrayList<TaskQuantum>();
                
                for (TaskQuantum tmpTaskQuantum : _taskQuantums)
                    if (tmpTaskQuantum.getTask() == tmpTask)
                        tmpTaskQuantumOrphans.add(tmpTaskQuantum);
                
                _taskQuantums.removeAll(tmpTaskQuantumOrphans);
            }
        }
        catch (Exception e)
        {
            System.out.println("Error while writing the result of the task #" + tmpTaskID);
        }
    }
    
    private synchronized void loadTasks() throws Exception
    {
        FileInputStream tmpInput = new FileInputStream(".\\tasks");
        InputStreamReader tmpInputReader = new InputStreamReader(tmpInput);
        BufferedReader tmpBufferedReader = new BufferedReader(tmpInputReader);

        Set<Integer> tmpForgottenIDs = new HashSet<Integer>(_taskIndex.keySet());
        String tmpLine, tmpBlock = "", tmpPrefix;
        int tmpID, tmpPriority, tmpDateFrom, tmpDateTo, tmpDayFrom, tmpDayTo, tmpHourFrom, tmpHourTo;
        Matcher tmpMatcher;
        
        do
        {
            if ((tmpLine = tmpBufferedReader.readLine()) == null || (tmpLine = tmpLine.trim()).equals(""))
            {
                if (!tmpBlock.equals(""))
                {
                    System.out.print("Task #");
                    
                    try
                    {
                        tmpMatcher = _IDPattern.matcher(tmpBlock);
                        if (tmpMatcher.find()) tmpID = Integer.parseInt(tmpMatcher.group(1));
                        else throw new Exception("#: No ID field found");
                        if (tmpForgottenIDs.contains(tmpID)) tmpForgottenIDs.remove(tmpID);
                        System.out.print(tmpID + ": ");
                        if (tmpMatcher.find()) throw new Exception("Duplicate ID field found");
                        if (tmpID < 0) throw new Exception("Invalid ID field");
    
                        tmpMatcher = _priorityPattern.matcher(tmpBlock);
                        if (tmpMatcher.find()) tmpPriority = Integer.parseInt(tmpMatcher.group(1));
                        else throw new Exception("No Priority field found");
                        if (tmpMatcher.find()) throw new Exception("Duplicate Priority field found");
                        if (tmpPriority < 0) throw new Exception("Invalid Priority field");
                        
                        if (!_taskIndex.containsKey(tmpID))
                        {
                            tmpMatcher = _prefixPattern.matcher(tmpBlock);
                            if (tmpMatcher.find()) tmpPrefix = tmpMatcher.group(1);
                            else throw new Exception("No Prefix field found");
                            if (tmpMatcher.find()) throw new Exception("Duplicate Prefix field found");

                            tmpMatcher = _datePattern.matcher(tmpBlock);
                            if (tmpMatcher.find())
                            {
                                tmpDateFrom = Integer.parseInt(tmpMatcher.group(1));
                                tmpDateTo = Integer.parseInt(tmpMatcher.group(2));
                            }
                            else throw new Exception("No Date field found");
                            if (tmpMatcher.find()) throw new Exception("Duplicate Date field found");
                            if (tmpDateFrom < 0 || tmpDateTo < tmpDateFrom) throw new Exception("Invalid Date field");

                            tmpMatcher = _dayPattern.matcher(tmpBlock);
                            if (tmpMatcher.find())
                            {
                                tmpDayFrom = Integer.parseInt(tmpMatcher.group(1));
                                tmpDayTo = Integer.parseInt(tmpMatcher.group(2));
                                if (tmpMatcher.find()) throw new Exception("Duplicate Day field found");
                            }
                            else
                            {
                                tmpDayFrom = 1;
                                tmpDayTo = 31;
                            }
                            if (tmpDayFrom < 1 || tmpDayTo > 31 || tmpDayTo < tmpDayFrom) throw new Exception("Invalid Day field");

                            tmpMatcher = _hourPattern.matcher(tmpBlock);
                            if (tmpMatcher.find())
                            {
                                tmpHourFrom = Integer.parseInt(tmpMatcher.group(1));
                                tmpHourTo = Integer.parseInt(tmpMatcher.group(2));
                                if (tmpMatcher.find()) throw new Exception("Duplicate Hour field found");
                            }
                            else
                            {
                                tmpHourFrom = 0;
                                tmpHourTo = 23;
                            }
                            if (tmpHourFrom < 0 || tmpHourTo > 23 || tmpHourTo < tmpHourFrom) throw new Exception("Invalid Hour field");
                            
                            _taskIndex.put(tmpID, new Task(tmpPriority, tmpPrefix, tmpDateFrom, tmpDateTo, tmpDayFrom, tmpDayTo, tmpHourFrom, tmpHourTo));
                            _noQuantums = false;
                            System.out.println("LOADED");
                        }
                        else
                        {
                            int tmpOldPriority = _taskIndex.get(tmpID).getPriority();
                            _taskIndex.get(tmpID).setPriority(tmpPriority);
                            
                            if (tmpPriority != tmpOldPriority)
                                System.out.println("UPDATED");
                            else
                                System.out.println("NOTHING CHANGED");
                        }
                    }
                    catch (Exception e)
                    {
                        System.out.println(e.getMessage());
                    }
                    
                    tmpBlock = "";
                }
                
                continue;
            }

            tmpBlock += tmpLine;
        }
        while (tmpLine != null);
        
        for (int tmpForgottenID : tmpForgottenIDs)
        {
            _taskIndex.remove(tmpForgottenID);
            System.out.println("Task #" + tmpForgottenID + ": REMOVED");
        }
        
        _tasks = new ArrayList<Task>(_taskIndex.values());
        
        if (_tasks.size() == 0)
            _noQuantums = true;
    }
}
