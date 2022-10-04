terraform {

  required_version = "~> 1.3.0"

  backend "local" {
    path = "state/terraform.tfstate"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "4.33.0"
    }
  }
}

provider "aws" {
  region = local.region

  default_tags {
    tags = {
      ManagedBy = "terraform"
      Owner     = "jan_ludwig"
    }
  }
}