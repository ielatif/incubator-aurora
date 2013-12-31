package org.apache.aurora.scheduler.storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import com.twitter.common.util.testing.FakeClock;

import org.apache.aurora.gen.AssignedTask;
import org.apache.aurora.gen.Constraint;
import org.apache.aurora.gen.Identity;
import org.apache.aurora.gen.LimitConstraint;
import org.apache.aurora.gen.ScheduleStatus;
import org.apache.aurora.gen.ScheduledTask;
import org.apache.aurora.gen.TaskConfig;
import org.apache.aurora.gen.TaskConstraint;
import org.apache.aurora.scheduler.base.Query;
import org.apache.aurora.scheduler.storage.Storage.MutableStoreProvider;
import org.apache.aurora.scheduler.storage.Storage.MutateWork;
import org.apache.aurora.scheduler.storage.entities.IScheduledTask;
import org.apache.aurora.scheduler.storage.mem.MemStorage;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StorageBackfillTest {

  private Storage storage;
  private FakeClock clock;

  @Before
  public void setUp() {
    storage = MemStorage.newEmptyStorage();
    clock = new FakeClock();
  }

  private static IScheduledTask makeTask(String id, int instanceId) {

    TaskConfig config = new TaskConfig()
        .setOwner(new Identity("user", "role"))
        .setEnvironment("test")
        .setJobName("jobName")
        .setProduction(false)
        .setConstraints(ImmutableSet.of(
            new Constraint("host", TaskConstraint.limit(new LimitConstraint(1)))))
        .setRequestedPorts(ImmutableSet.<String>of())
        .setMaxTaskFailures(1)
        .setTaskLinks(ImmutableMap.<String, String>of());
    ScheduledTask task = new ScheduledTask().setAssignedTask(
        new AssignedTask().setTask(config));
    task.getAssignedTask().setTaskId(id);
    task.getAssignedTask().setInstanceId(instanceId);
    return IScheduledTask.build(task);
  }

  @Test
  public void testRewriteThrottledState() {
    final IScheduledTask savedTask =
        IScheduledTask.build(makeTask("id", 0).newBuilder().setStatus(ScheduleStatus.THROTTLED));

    storage.write(new MutateWork.NoResult.Quiet() {
      @Override protected void execute(MutableStoreProvider storeProvider) {
        storeProvider.getUnsafeTaskStore().saveTasks(ImmutableSet.of(savedTask));
        StorageBackfill.backfill(storeProvider, clock);
      }
    });

    assertEquals(
        IScheduledTask.build(savedTask.newBuilder().setStatus(ScheduleStatus.PENDING)),
        getTask("id"));
  }

  private IScheduledTask getTask(final String id) {
    return Iterables.getOnlyElement(
        Storage.Util.consistentFetchTasks(storage, Query.taskScoped(id)));
  }
}
