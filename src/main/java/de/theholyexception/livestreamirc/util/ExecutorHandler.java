package de.theholyexception.livestreamirc.util;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ExecutorHandler {

    private int lastTaskIdentifier = 0;

    @Getter
    private final List<ExecutorTask> taskList;
    private final ExecutorService executorService;

    public ExecutorHandler(ExecutorService executorService) {
        this.executorService = executorService;
        this.taskList = new ArrayList<>();
    }

    public int getNewTaskIdentifier() {
        return lastTaskIdentifier++;
    }

    public void putTask(ExecutorTask task, int groupID) {
        task.setGroupId(groupID);
        task.setTaskId(getNewTaskIdentifier());
        taskList.add(task);
        executorService.execute(task);
    }

    public void putTask(ExecutorTask task) {
        putTask(task, -1);
    }

    public boolean hasGroupRunningThreads(int groupID) {
        return taskList.stream()
                .anyMatch(t -> (t.getGroupId() == groupID) && !t.isCompleted());
    }

    public boolean areThreadsRunning() {
        return taskList.stream().anyMatch(t -> !t.isCompleted());
    }

    public void awaitGroup(int groupID, long checkInterval) {
        while (hasGroupRunningThreads(groupID)) {
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void awaitGroup(int groupID) {
        awaitGroup(groupID, 100);
    }

    public void closeAfterExecution() {
        while (taskList.stream().anyMatch(t -> !t.isCompleted())) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        executorService.shutdown();
    }

    public void closeForce() {
         executorService.shutdownNow();
    }

}
