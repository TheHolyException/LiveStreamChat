package de.theholyexception.livestreamirc.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecutorTask implements Runnable {

    private final Runnable command;
    private int taskId;
    private int groupId;
    private boolean completed;
    private long starttime;
    private long runtime;

    public ExecutorTask(Runnable command) {
        this.command = command;
        this.groupId = -1;
        this.taskId = -1;
        this.completed = false;
    }

    public ExecutorTask(Runnable command, int groupId) {
        this.command = command;
        this.groupId = groupId;
        this.taskId = -1;
        this.completed = false;
    }

    public boolean isCompleted() {
        return completed;
    }

    public long getRuntime() {
        runtime = System.currentTimeMillis()-starttime;
        return runtime;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        if (this.taskId == -1)
            this.taskId = taskId;
        else
            log.warn("Cannot set task id, this task already has an task id ("+this.taskId+")");
    }

    @Override
    public void run() {
        starttime = System.currentTimeMillis();
        try {
            command.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        completed = true;
        runtime = System.currentTimeMillis()-starttime;
    }

    @Override
    public String toString() {
        return String.format("ExecutorTask: {TaskID: %d, GroupID: %d, Completed: %b, Runtime: %dms}", taskId, groupId, completed, getRuntime());
    }
}
