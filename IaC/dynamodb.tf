resource "aws_dynamodb_table" "jalu_challenge_table" {
  name         = "JaluChallengeUsers"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "N"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

}