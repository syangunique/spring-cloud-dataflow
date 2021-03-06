/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.server.service.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDataSourceConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.configuration.TaskServiceDependencies;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.SchedulerService;
import org.springframework.cloud.scheduler.spi.core.CreateScheduleException;
import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.cloud.scheduler.spi.core.SchedulerPropertyKeys;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = { EmbeddedDataSourceConfiguration.class, TaskServiceDependencies.class,
		PropertyPlaceholderAutoConfiguration.class }, properties = {
		"spring.cloud.dataflow.applicationProperties.task.globalkey=globalvalue",
		"spring.cloud.dataflow.applicationProperties.stream.globalstreamkey=nothere" })
@EnableConfigurationProperties({ CommonApplicationProperties.class })
public class DefaultSchedulerServiceTests {
	private static final String SCHEDULER_PREFIX = "scheduler.demo.";

	private static final String BASE_SCHEDULE_NAME = "myTaskDefinition";

	private static final String BASE_DEFINITION_NAME = "myTaskDefinition";

	@Autowired
	private Scheduler simpleTestScheduler;

	@Autowired
	private SchedulerService schedulerService;

	@Autowired
	private TaskDefinitionRepository taskDefinitionRepository;

	@Autowired
	private AppRegistry appRegistry;

	private Map<String, String> testProperties;

	private Map<String, String> resolvedProperties;

	List<String> commandLineArgs;

	@Before
	public void setup() throws Exception{
		this.appRegistry.save("demo", ApplicationType.task, new URI("file:src/test/resources/apps/foo-task"), new URI("file:src/test/resources/apps/foo-task"));
		taskDefinitionRepository.save(new TaskDefinition(BASE_DEFINITION_NAME, "demo"));
		initializeSuccessfulRegistry();

		this.testProperties = new HashMap<>();
		this.testProperties.put(SCHEDULER_PREFIX + "AAAA" , "* * * * *");
		this.testProperties.put(SCHEDULER_PREFIX + SchedulerPropertyKeys.CRON_EXPRESSION, "* * * * *");
		this.resolvedProperties = new HashMap<>();
		this.resolvedProperties.put("AAAA", "* * * * *");
		this.resolvedProperties.put(SchedulerPropertyKeys.CRON_EXPRESSION, "* * * * *");
		this.commandLineArgs = new ArrayList<>();
	}

	@After
	public void tearDown() {
		((TaskServiceDependencies.SimpleTestScheduler)simpleTestScheduler).getSchedules().clear();
	}

	@Test
	@DirtiesContext
	public void testSchedule(){
		schedulerService.schedule(BASE_SCHEDULE_NAME, BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME));
	}

	@Test(expected = CreateScheduleException.class)
	@DirtiesContext
	public void testDuplicate(){
		schedulerService.schedule(BASE_SCHEDULE_NAME + 1, BASE_DEFINITION_NAME,
				this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 1, BASE_DEFINITION_NAME,
				this.testProperties, this.commandLineArgs);
	}

	@Test
	@DirtiesContext
	public void testMultipleSchedules(){
		schedulerService.schedule(BASE_SCHEDULE_NAME + 1,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 2,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 3,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);

		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 1));
		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 2));
		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 3));
	}

	@Test
	@DirtiesContext
	public void testUnschedule(){
		schedulerService.schedule(BASE_SCHEDULE_NAME + 1,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 2,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 3,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);

		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 1));
		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 2));
		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 3));

		schedulerService.unschedule(BASE_SCHEDULE_NAME + 2);
		validateSchedulesCount(2);
		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 1));
		verifyScheduleExistsInScheduler(createScheduleInfo(BASE_SCHEDULE_NAME + 3));

	}

	@Test
	@DirtiesContext
	public void testEmptyUnschedule(){
		validateSchedulesCount(0);
		schedulerService.unschedule(BASE_SCHEDULE_NAME + 2);
		validateSchedulesCount(0);
	}

	@Test
	@DirtiesContext
	public void testList(){
		schedulerService.schedule(BASE_SCHEDULE_NAME + 1,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 2,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 3,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);

		List<ScheduleInfo> schedules = schedulerService.list(null);
		assertThat(schedules.size()).isEqualTo(3);
		verifyScheduleExistsInScheduler(schedules.get(0));
		verifyScheduleExistsInScheduler(schedules.get(1));
		verifyScheduleExistsInScheduler(schedules.get(2));

	}

	@Test
	@DirtiesContext
	public void testListWithParams() {
		taskDefinitionRepository.save(new TaskDefinition(BASE_DEFINITION_NAME + 1, "demo"));
		schedulerService.schedule(BASE_SCHEDULE_NAME + 1,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 2,
				BASE_DEFINITION_NAME + 1, this.testProperties, this.commandLineArgs);
		schedulerService.schedule(BASE_SCHEDULE_NAME + 3,
				BASE_DEFINITION_NAME, this.testProperties, this.commandLineArgs);

		List<ScheduleInfo> schedules = schedulerService.list(null, BASE_DEFINITION_NAME + 1);
		assertThat(schedules.size()).isEqualTo(1);
		verifyScheduleExistsInScheduler(schedules.get(0));
	}

	@Test
	@DirtiesContext
	public void testEmptyList() {
		taskDefinitionRepository.save(new TaskDefinition(BASE_DEFINITION_NAME + 1, "demo"));
		List<ScheduleInfo> schedules = schedulerService.list(null, BASE_DEFINITION_NAME + 1);
		assertThat(schedules.size()).isEqualTo(0);
		schedules = schedulerService.list(null);
		assertThat(schedules.size()).isEqualTo(0);
	}


	private void verifyScheduleExistsInScheduler(ScheduleInfo scheduleInfo) {
		List<ScheduleInfo> scheduleInfos = ((TaskServiceDependencies.SimpleTestScheduler)simpleTestScheduler).getSchedules();
		scheduleInfos = scheduleInfos.stream().filter(s -> s.getScheduleName().
				equals(scheduleInfo.getScheduleName())).
				collect(Collectors.toList());

		assertThat(scheduleInfos.size()).isEqualTo(1);
		assertThat(scheduleInfos.get(0).getTaskDefinitionName()).isEqualTo(
				scheduleInfo.getTaskDefinitionName());

		for(String key: scheduleInfo.getScheduleProperties().keySet()) {
			assertThat(scheduleInfos.get(0).getScheduleProperties().
					get(key)).
					isEqualTo(scheduleInfo.getScheduleProperties().get(key));
		}
	}

	private void validateSchedulesCount(int expectedScheduleCount) {
		assertThat(((TaskServiceDependencies.SimpleTestScheduler)simpleTestScheduler).
				getSchedules().size()).isEqualTo(expectedScheduleCount);
	}

	private ScheduleInfo createScheduleInfo(String scheduleName) {
		ScheduleInfo scheduleInfo = new ScheduleInfo();
		scheduleInfo.setScheduleName(scheduleName);
		scheduleInfo.setTaskDefinitionName(BASE_DEFINITION_NAME);
		scheduleInfo.setScheduleProperties(this.resolvedProperties);
		return scheduleInfo;
	}

	private void initializeSuccessfulRegistry() {
		when(this.appRegistry.find(anyString(), any(ApplicationType.class)))
				.thenReturn(new AppRegistration("demo", ApplicationType.task, URI.create("http://helloworld")));
		when(this.appRegistry.getAppResource(any())).thenReturn(mock(Resource.class));
		when(this.appRegistry.getAppMetadataResource(any())).thenReturn(null);
	}

}
