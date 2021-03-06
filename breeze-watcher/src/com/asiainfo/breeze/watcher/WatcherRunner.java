package com.asiainfo.breeze.watcher;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.bson.Document;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import com.asiainfo.breeze.util.InstanceHolder;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.MongoClientOptions.Builder;

/**
 * breeze-watcher启动类
 * @author kelgon
 *
 */
public class WatcherRunner {
	private static Logger log = Logger.getLogger(WatcherRunner.class);
	

	/**
	 * 初始化MongoDB client
	 * @return
	 */
	private static boolean initMongo() {
		try {
			log.info("loading breeze-mongo.properties...");
			InputStream is = WatcherRunner.class.getClassLoader().getResourceAsStream("breeze-mongo.properties");
			Properties props = new Properties();
			props.load(is);

			//构造MongoDB集群serverList
			String servers = props.getProperty("mongo.servers");
			if("".equals(servers) || servers == null) {
				log.error("mongo.servers must not be null or empty!");
				return false;
			}
			List<ServerAddress> serverList = new ArrayList<ServerAddress>();
			for(String s : servers.split(",")) {
				String[] addr = s.split(":");
				ServerAddress sa = new ServerAddress(addr[0], Integer.parseInt(addr[1]));
				serverList.add(sa);
			}

			//构造MongoDB身份认证对象
			String adminDbName = props.getProperty("breeze.adminDbName");
			if("".equals(adminDbName) || adminDbName == null) {
				log.error("breeze.adminDbName must not be null or empty!");
				return false;
			}
			String adminCredentials = props.getProperty("breeze.adminCredentials");
			List<MongoCredential> mCreList = new ArrayList<MongoCredential>();
			if(!"".equals(adminCredentials) && adminCredentials != null) {
				String[] cre = adminCredentials.split(":");
				MongoCredential credential = MongoCredential.createScramSha1Credential(cre[0], adminDbName, cre[1].toCharArray());
				mCreList.add(credential);
			}

			String monitorDbName = props.getProperty("breeze.monitorDbName");
			if("".equals(monitorDbName) || monitorDbName == null) {
				log.error("breeze.monitorDbName must not be null or empty!");
				return false;
			}
			String monitorCredentials = props.getProperty("breeze.monitorCredentials");
			if(!"".equals(monitorCredentials) && monitorCredentials != null) {
				String[] cre = monitorCredentials.split(":");
				MongoCredential credential = MongoCredential.createScramSha1Credential(cre[0], monitorDbName, cre[1].toCharArray());
				mCreList.add(credential);
			}

			String recordDbName = props.getProperty("breeze.recordDbName");
			if("".equals(recordDbName) || recordDbName == null) {
				log.error("breeze.recordDbName must not be null or empty!");
				return false;
			}
			String recordCredentials = props.getProperty("breeze.recordCredentials");
			if(!"".equals(recordCredentials) && recordCredentials != null) {
				String[] cre = recordCredentials.split(":");
				MongoCredential credential = MongoCredential.createScramSha1Credential(cre[0], recordDbName, cre[1].toCharArray());
				mCreList.add(credential);
			}

			//从配置文件加载MongoDB客户端参数
			Builder options = new MongoClientOptions.Builder();
			if(props.containsKey("mongo.connectionsPerHost"))
				options.connectionsPerHost(Integer.parseInt(props.getProperty("mongo.connectionsPerHost")));
			if(props.containsKey("mongo.connectTimeout"))
				options.connectTimeout(Integer.parseInt(props.getProperty("mongo.connectTimeout")));
			if(props.containsKey("mongo.heartbeatConnectTimeout"))
				options.heartbeatConnectTimeout(Integer.parseInt(props.getProperty("mongo.heartbeatConnectTimeout")));
			if(props.containsKey("mongo.heartbeatFrequency"))
				options.heartbeatFrequency(Integer.parseInt(props.getProperty("mongo.heartbeatFrequency")));
			if(props.containsKey("mongo.heartbeatSocketTimeout"))
				options.heartbeatSocketTimeout(Integer.parseInt(props.getProperty("mongo.heartbeatSocketTimeout")));
			if(props.containsKey("mongo.maxConnectionIdleTime"))
				options.connectTimeout(Integer.parseInt(props.getProperty("mongo.maxConnectionIdleTime")));
			if(props.containsKey("mongo.maxConnectionLifeTime"))
				options.maxConnectionLifeTime(Integer.parseInt(props.getProperty("mongo.maxConnectionLifeTime")));
			if(props.containsKey("mongo.maxWaitTime"))
				options.maxWaitTime(Integer.parseInt(props.getProperty("mongo.maxWaitTime")));
			if(props.containsKey("mongo.minConnectionsPerHost"))
				options.minConnectionsPerHost(Integer.parseInt(props.getProperty("mongo.minConnectionsPerHost")));
			if(props.containsKey("mongo.minHeartbeatFrequency"))
				options.minHeartbeatFrequency(Integer.parseInt(props.getProperty("mongo.minHeartbeatFrequency")));
			if(props.containsKey("mongo.readConcern")) {
				String readConcern = props.getProperty("mongo.readConcern");
				if("default".equalsIgnoreCase(readConcern))
					options.readConcern(ReadConcern.DEFAULT);
				if("local".equalsIgnoreCase(readConcern))
					options.readConcern(ReadConcern.LOCAL);
				if("majority".equalsIgnoreCase(readConcern))
					options.readConcern(ReadConcern.MAJORITY);
			}
			if(props.containsKey("mongo.readPreference")) {
				String readPreference = props.getProperty("mongo.readPreference");
				if("primary".equalsIgnoreCase(readPreference))
					options.readPreference(ReadPreference.primary());
				if("primaryPreferred".equalsIgnoreCase(readPreference))
					options.readPreference(ReadPreference.primaryPreferred());
				if("secondary".equalsIgnoreCase(readPreference))
					options.readPreference(ReadPreference.secondary());
				if("secondaryPreferred".equalsIgnoreCase(readPreference))
					options.readPreference(ReadPreference.secondaryPreferred());
				if("nearest".equalsIgnoreCase(readPreference))
					options.readPreference(ReadPreference.nearest());
			}
			if(props.containsKey("mongo.serverSelectionTimeout"))
				options.serverSelectionTimeout(Integer.parseInt(props.getProperty("mongo.serverSelectionTimeout")));
			if(props.containsKey("mongo.socketTimeout"))
				options.socketTimeout(Integer.parseInt(props.getProperty("mongo.socketTimeout")));
			if(props.containsKey("mongo.threadsAllowedToBlockForConnectionMultiplier"))
				options.threadsAllowedToBlockForConnectionMultiplier(Integer.parseInt(props.getProperty("mongo.threadsAllowedToBlockForConnectionMultiplier")));
			if(props.containsKey("mongo.writeConcern"))
				options.writeConcern(new WriteConcern(Integer.parseInt(props.getProperty("mongo.writeConcern"))));
			if(props.containsKey("mongo.socketKeepAlive"))
				options.socketKeepAlive(Boolean.parseBoolean(props.getProperty("mongo.socketKeepAlive")));
			if(props.containsKey("mongo.sslEnabled"))
				options.sslEnabled(Boolean.parseBoolean(props.getProperty("mongo.sslEnabled")));
			if(props.containsKey("mongo.sslInvalidHostNameAllowed"))
				options.sslInvalidHostNameAllowed(Boolean.parseBoolean(props.getProperty("mongo.sslInvalidHostNameAllowed")));
			
			//initialize mongodb client
			log.info("initializing mongodb client...");
			if(mCreList.size() > 0)
				InstanceHolder.mClient = new MongoClient(serverList, mCreList, options.build());
			else
				InstanceHolder.mClient = new MongoClient(serverList);
			InstanceHolder.monitorMdb = InstanceHolder.mClient.getDatabase(monitorDbName);
			InstanceHolder.recordMdb = InstanceHolder.mClient.getDatabase(recordDbName);

			//获取集群状态并保存至Map中
			Document rsStatus = InstanceHolder.mClient.getDatabase("admin").runCommand(new Document("replSetGetStatus", "1"));
			for(Object o : ((ArrayList<?>)rsStatus.get("members"))) {
				Document doc = (Document)o;
				log.info("Mongo node [" + doc.getString("name") + "] status: [" + doc.getInteger("state") + ":" +doc.getString("stateStr") + "]");
				InstanceHolder.mongoServers.put(doc.getString("name"), doc.getString("stateStr"));
			}
			
			return true;
		} catch(Throwable t) {
			log.error("initializing MongoClient failed", t);
			return false;
		}
	}
	

	/**
	 * 初始化KafkaProducer client
	 * @return
	 */
	private static boolean initKafkaProducer() {
		try {
			log.info("loading breeze-kafka.properties...");
			InputStream is = WatcherRunner.class.getClassLoader().getResourceAsStream("breeze-kafka.properties");
			Properties props = new Properties();
			props.load(is);
			InstanceHolder.kp = new KafkaProducer<String, String>(props);
			return true;
		} catch(Throwable t) {
			log.error("initializing kafka client failed", t);
			return false;
		}
	}
	
	/**
	 * 初始化Quartz
	 * @return
	 */
	private static boolean initQuartz() {
		try {
			log.info("loading breeze-watcher.properties...");
			InputStream is = WatcherRunner.class.getClassLoader().getResourceAsStream("breeze-watcher.properties");
			Properties props = new Properties();
			props.load(is);
		    SchedulerFactory sf = new StdSchedulerFactory();
		    Scheduler sched = sf.getScheduler();
		    
		    JobDetail KafkaClusterHealthCheckJob = JobBuilder.newJob(KafkaClusterHealthCheckJob.class).withIdentity("KafkaClusterHealthCheckJob", "group1").build();
		    String KafkaClusterHealthCheckCron = props.getProperty("KafkaClusterHealthCheckJob.cron");
		    if("".equals(KafkaClusterHealthCheckCron) || KafkaClusterHealthCheckCron == null) {
				log.error("KafkaClusterHealthCheckJob.cron must not be null or empty!");
		    }
		    CronTrigger KafkaClusterHealthCheckTrigger = (CronTrigger)TriggerBuilder.newTrigger().withIdentity("KafkaClusterHealthCheckJobTrigger", "group1").withSchedule(CronScheduleBuilder.cronSchedule(KafkaClusterHealthCheckCron)).build();
		    sched.scheduleJob(KafkaClusterHealthCheckJob, KafkaClusterHealthCheckTrigger);
		    
		    JobDetail MongoReplicaHealthCheckJob = JobBuilder.newJob(MongoReplicaHealthCheckJob.class).withIdentity("MongoReplicaHealthCheckJob", "group1").build();
		    String MongoReplicaHealthCheckCron = props.getProperty("MongoReplicaHealthCheckJob.cron");
		    if("".equals(MongoReplicaHealthCheckCron) || MongoReplicaHealthCheckCron == null) {
				log.error("KafkaClusterHealthCheckJob.cron must not be null or empty!");
		    }
		    CronTrigger MongoReplicaHealthCheckTrigger = (CronTrigger)TriggerBuilder.newTrigger().withIdentity("MongoReplicaHealthCheckJobTrigger", "group1").withSchedule(CronScheduleBuilder.cronSchedule(MongoReplicaHealthCheckCron)).build();
		    sched.scheduleJob(MongoReplicaHealthCheckJob, MongoReplicaHealthCheckTrigger);
		    
		    JobDetail ZookeeperHealthCheckJob = JobBuilder.newJob(ZookeeperHealthCheckJob.class).withIdentity("ZookeeperHealthCheckJob", "group1").build();
		    String ZookeeperHealthCheckCron = props.getProperty("ZookeeperHealthCheckJob.cron");
		    if("".equals(ZookeeperHealthCheckCron) || ZookeeperHealthCheckCron == null) {
				log.error("ZookeeperHealthCheckJob.cron must not be null or empty!");
		    }
		    CronTrigger ZookeeperHealthCheckTrigger = (CronTrigger)TriggerBuilder.newTrigger().withIdentity("ZookeeperHealthCheckJob", "group1").withSchedule(CronScheduleBuilder.cronSchedule(ZookeeperHealthCheckCron)).build();
		    sched.scheduleJob(ZookeeperHealthCheckJob, ZookeeperHealthCheckTrigger);
		    
		    JobDetail DefinedMonitorJob = JobBuilder.newJob(DefinedMonitorJob.class).withIdentity("DefinedMonitorJob", "group1").build();
		    String DefinedMonitorCron = props.getProperty("DefinedMonitorJob.cron");
		    if("".equals(DefinedMonitorCron) || DefinedMonitorCron == null) {
				log.error("DefinedMonitorJob.cron must not be null or empty!");
		    }
		    CronTrigger DefinedMonitorTrigger = (CronTrigger)TriggerBuilder.newTrigger().withIdentity("DefinedMonitorJob", "group1").withSchedule(CronScheduleBuilder.cronSchedule(DefinedMonitorCron)).build();
		    sched.scheduleJob(DefinedMonitorJob, DefinedMonitorTrigger);
		    
		    JobDetail CollectionCreateJob = JobBuilder.newJob(CollectionCreateJob.class).withIdentity("CollectionCreateJob", "group1").build();
		    String CollectionCreateCron = props.getProperty("CollectionCreateJob.cron");
		    if("".equals(CollectionCreateCron) || CollectionCreateCron == null) {
				log.error("CollectionCreateJob.cron must not be null or empty!");
		    }
		    CronTrigger CollectionCreateTrigger = (CronTrigger)TriggerBuilder.newTrigger().withIdentity("CollectionCreateJob", "group1").withSchedule(CronScheduleBuilder.cronSchedule(CollectionCreateCron)).build();
		    sched.scheduleJob(CollectionCreateJob, CollectionCreateTrigger);
		    
		    sched.start();
		    return true;
		} catch(Throwable t) {
			log.error("initializing quartz failed", t);
			return false;
		}
	}
	
	public static void main(String[] args) {
		PropertyConfigurator.configure(WatcherRunner.class.getClassLoader().getResource("log4j.properties"));
		log.info("initializing mongo client...");
		if(WatcherRunner.initMongo()) {
			log.info("initializing kafka client...");
			if(WatcherRunner.initKafkaProducer()) {
				log.info("initializing quartz jobs...");
				if(WatcherRunner.initQuartz()) {
					log.info("breeze-watcher started");
					return;
				}
			}
		}
		log.error("failed to start breeze-consumer");
	}
}
