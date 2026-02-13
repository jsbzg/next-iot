
import sys
from kafka.admin import KafkaAdminClient, NewTopic
from kafka.errors import TopicAlreadyExistsError

BOOTSTRAP_SERVERS = 'localhost:9092'
PARTITIONS = 1
REPLICATION_FACTOR = 1

TOPICS = [
    'raw-topic',
    'config-topic',
    'alarm-event-topic',
    'metric-topic'
]

def create_topics():
    try:
        admin_client = KafkaAdminClient(bootstrap_servers=BOOTSTRAP_SERVERS)
        
        # Get existing topics
        existing_topics = admin_client.list_topics()
        print(f"Existing topics: {existing_topics}")
        
        new_topics = []
        for topic_name in TOPICS:
            if topic_name not in existing_topics:
                print(f"Preparing to create topic: {topic_name}")
                new_topics.append(NewTopic(
                    name=topic_name,
                    num_partitions=PARTITIONS, 
                    replication_factor=REPLICATION_FACTOR
                ))
            else:
                print(f"Topic {topic_name} already exists.")
        
        if new_topics:
            admin_client.create_topics(new_topics=new_topics)
            print(f"Successfully created topics: {[t.name for t in new_topics]}")
        else:
            print("All topics already exist.")
            
        admin_client.close()
        
    except Exception as e:
        print(f"Failed to create topics: {e}")
        sys.exit(1)

if __name__ == "__main__":
    create_topics()
