locals {
  region                  = "eu-west-1"
  basic_lambda_policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  path_to_deployable      = "${path.module}/../Greeting/target/Greeting-1.0.jar"
  backend_url             = "https://notification-backend-challenge.main.komoot.net/"
  push_event_sender       = "jan.ludwig.home@gmail.com"
}