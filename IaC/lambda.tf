resource "aws_sns_topic_subscription" "sns_subscription" {
  topic_arn = var.sns_topic_arn
  endpoint  = aws_lambda_function.jalu_test_lambda.arn
  protocol  = "lambda"
}

resource "aws_lambda_permission" "allow_sns_to_trigger_lambda" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.jalu_test_lambda.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = var.sns_topic_arn
}

resource "aws_iam_role" "jalu_lambda_iam_role" {
  name = "jalu_lambda_iam_role"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy" "jalu_lambda_inline_policy" {
  name = "jalu_lambda_inline_policy"
  role = aws_iam_role.jalu_lambda_iam_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid : "ReadWriteTable",
        Effect : "Allow",
        Action : [
          "dynamodb:BatchGetItem",
          "dynamodb:GetItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:BatchWriteItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem"
        ],
        Resource : "arn:aws:dynamodb:*:*:table/${aws_dynamodb_table.jalu_challenge_table.name}"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "logging" {
  role       = aws_iam_role.jalu_lambda_iam_role.name
  policy_arn = local.basic_lambda_policy_arn
}

resource "aws_cloudwatch_log_group" "lambda_log_group" {
  name              = "/aws/lambda/${aws_lambda_function.jalu_test_lambda.function_name}"
  retention_in_days = 1
}

resource "aws_lambda_function" "jalu_test_lambda" {
  filename         = local.path_to_deployable
  source_code_hash = filebase64sha256(local.path_to_deployable)

  function_name = "jalu_test_lambda"
  role          = aws_iam_role.jalu_lambda_iam_role.arn
  runtime       = "java11"
  timeout       = 60
  memory_size   = 256
  handler       = "jalu.challenge.Handler::handleRequest"

  environment {
    variables = {
      TABLE             = aws_dynamodb_table.jalu_challenge_table.name
      BACKEND_URL       = local.backend_url
      PUSH_EVENT_SENDER = local.push_event_sender
    }
  }
}