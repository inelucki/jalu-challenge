variable "sns_topic_arn" {
  type        = string
  description = "arn of the SNS topic to subscribe to"
  default = "arn:aws:sns:eu-west-1:963797398573:challenge-backend-signups"
}