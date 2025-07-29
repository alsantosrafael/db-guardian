terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.81.0"
    }
  }
}
# Provider


provider "aws" {
  region = "us-east-1"
}

# bucket

resource "aws_s3_bucket" "reports" {
  bucket = "query-analyzer-reports"
}