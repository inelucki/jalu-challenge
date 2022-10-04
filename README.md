# jalu-challenge


## What does it do?

Terraform is creating a lambda function with the SNS topic as trigger. It also provides a dynamodb Table as
persistence layer for the code.
The deployed code is doing the following:
* receiving the notification from SNS
* checking if that event was already processed
* pulling possible connections from dynamodb
* send a pushEvent via REST endpoint to the backend
* persist the newly processed event

## Setup

### Prerequisites
* clone the repo so that you can execute the code locally
* install terraform
* install maven
* install java 11 (or higher)
* install aws-cli

### Deployment

These instructions will build an artifact, set up the whole infrastructure in AWS and deploy the artifact to the cloud.

```
aws sso login  # or something similar to provide env vars for terraform

cd Greeting
mvn clean install

cd ../IaC
terraform init
terraform apply

```
Now everything is ready, so you can start pushing events to SNS.


## Considerations for the selected Infrastructure components

### Lambda

I selected AWS Lambda to run the code mainly because it was the simplest approach. Without having ECS or EKS at hand
it was faster to set up a Lambda. In a context where ECS or EKS is available, I'd prefer to deploy this code to either 
of those.
If the code should be executed only via trigger (not running 24/7) or should permanently run as a service depends on the
amount of incoming events and the time distribution of those events. As I don't have the information I "guessed" that
Lambda would be suitable. In real life I would base this decision on some monitoring and evaluation.

### DynamoDb

It was apparent that the code will need a persistence layer or some caching to store information about possible
connections to other users. I also thought about using ElasticCache or S3 (storing a small file) to do so. All 
options were valid, so I opted for DynamoDb as the simplest approach.
As a completely different approach I was also thinking about using SNS as fan-out to Lambda and SQS in parallel. The
Lambda could then be triggered by SNS (knowing the exact event) and pulling up to ten items from SQS to figure out 
possible connections. I didn't like this approach in the end, because the management of items to be deleted in SQS was
way more cumbersome than with dynamoDb. In dynamoDb I added the ttl property to every entry, so that dynamoDb takes
care of deleting those entries automatically. As a threshold for deletion I set one day. This is a heuristic and
my best guess on the information I have. In real life this heuristic should be based on monitoring and evaluation.

### Terraform

Cloudformation and SAM (as Lambda is the biggest part) would be suitable as well. I personally prefer Terraform over
those, because for me Terraform feels more consistent and is better documented.
Terraform is configured to use a local state for simplicity. To mention it explicitly,
that part is not production ready. ;) 

### JAR vs Docker

Lambda is providing a containerized runtime even though I only upload a .jar. So building a docker image and deploy
that instead would have been an overhead without any reasonable benefits. That also would have included setting up ECR
(or similar) to store the image.


## Monitoring, Logging, Tracing

Logs are send to Cloudwatch and I tried to add meaningful logs. Even though I have to admit that I might have added
too many logs to make up for the missing custom metrics. Monitoring wise I think the default metrics from Lambda are
sufficient for this use-case. I did not enable X-Ray-Tracing, because we could only have a look at single-hop traces
which doesn't bring a lot ov value.
I would have approached this topic very different in a real environment. But this is heavily relying on the available
tooling and common practices.


## Remarks about the Code

I made a couple of compromises within the code to be able to stick to the estimated timeframe. Which means I see
a lot of potential to improve the code. Mostly adding proper error handling and adding more and different tests.

The whole configuration of the code is injected via env vars of the Lambda. Meaning this code can easily be deployed
to different environments immediately. For the moment I just pulled the env vars where I needed them, an improvement 
would be to extract that into a configuration bean.

To interact with dynamoDb I did not use the enriched client. I believe that would be an improvement as well. 
Especially the ability to directly work on the entities instead of the Map would be safer and better to read. I sticked
to the simple client due to time constraints.

To pull possible connections from the dynamoDb I used the "scan"-operation with a limit of fifty items. DynamoDb is 
configured to delete entries on its own to ensure that the db is not getting too big. Nevertheless, (depending on 
the amount of events) the db can be way bigger than necessary. Therefore, I added the fallback with the limit to ensure
the code is working with a suitable amount of events. In that part I also shuffled the elements to provide some
"random" connections.