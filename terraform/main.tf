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

# Data Source - Retrieving user info
data "aws_caller_identity" "current" {}

# Bucket definition
resource "aws_s3_bucket" "reports" {
  bucket = "dbguardian-reports"
}

# Cryptography
resource "aws_s3_bucket_server_side_encryption_configuration" "reports" {
  bucket = aws_s3_bucket.reports.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
# Blocking public access
resource "aws_s3_bucket_public_access_block" "reports" {
  bucket = aws_s3_bucket.reports.id

  block_public_acls = true
  block_public_policy = true
  ignore_public_acls = true
  restrict_public_buckets = true
}

# Automatic backup
resource "aws_s3_bucket_versioning" "reports" {
  bucket = aws_s3_bucket.reports.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Automatic removal of files
resource "aws_s3_bucket_lifecycle_configuration" "reports" {
  bucket = aws_s3_bucket.reports.id

  rule {
    id     = "auto_cleanup"
    status = "Enabled"

    expiration {
      days = 30
    }
  }
}


# Protected URL only
resource "aws_s3_bucket_policy" "reports" {
  bucket = aws_s3_bucket.reports.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowApplicationAccess"
        Effect    = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/dbguardian-role"
        }
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.reports.arn}/*"
      },
      {
        Sid       = "DenyDirectPublicAccess"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.reports.arn}/*"
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      },
      {
        Sid       = "RequireSSLRequestsOnly"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.reports.arn,
          "${aws_s3_bucket.reports.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
  depends_on = [aws_s3_bucket_public_access_block.reports]
}
