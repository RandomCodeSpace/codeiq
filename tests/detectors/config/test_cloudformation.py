"""Tests for AWS CloudFormation detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.config.cloudformation import CloudFormationDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(
    parsed_data=None,
    file_path: str = "infra/template.yaml",
    language: str = "yaml",
) -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language=language,
        content=b"",
        parsed_data=parsed_data,
        module_name="test-module",
    )


class TestCloudFormationDetector:
    def setup_method(self):
        self.detector = CloudFormationDetector()

    def test_name_and_languages(self):
        assert self.detector.name == "cloudformation"
        assert self.detector.supported_languages == ("yaml", "json")

    # --- Positive: Resource detection ---

    def test_detects_single_resource(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "MyBucket": {
                        "Type": "AWS::S3::Bucket",
                        "Properties": {
                            "BucketName": "my-bucket",
                        },
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        resources = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE]
        assert len(resources) == 1
        assert resources[0].id == "cfn:infra/template.yaml:resource:MyBucket"
        assert resources[0].properties["resource_type"] == "AWS::S3::Bucket"
        assert resources[0].properties["logical_id"] == "MyBucket"

    def test_detects_multiple_resources(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "MyBucket": {
                        "Type": "AWS::S3::Bucket",
                    },
                    "MyQueue": {
                        "Type": "AWS::SQS::Queue",
                    },
                    "MyTable": {
                        "Type": "AWS::DynamoDB::Table",
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        resources = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE]
        assert len(resources) == 3
        types = {r.properties["resource_type"] for r in resources}
        assert "AWS::S3::Bucket" in types
        assert "AWS::SQS::Queue" in types
        assert "AWS::DynamoDB::Table" in types

    # --- Positive: Ref / GetAtt dependency detection ---

    def test_detects_ref_dependency(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "MyVPC": {
                        "Type": "AWS::EC2::VPC",
                        "Properties": {"CidrBlock": "10.0.0.0/16"},
                    },
                    "MySubnet": {
                        "Type": "AWS::EC2::Subnet",
                        "Properties": {
                            "VpcId": {"Ref": "MyVPC"},
                            "CidrBlock": "10.0.1.0/24",
                        },
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) == 1
        assert dep_edges[0].source == "cfn:infra/template.yaml:resource:MySubnet"
        assert dep_edges[0].target == "cfn:infra/template.yaml:resource:MyVPC"

    def test_detects_getatt_dependency(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "MyBucket": {
                        "Type": "AWS::S3::Bucket",
                    },
                    "MyPolicy": {
                        "Type": "AWS::IAM::Policy",
                        "Properties": {
                            "PolicyDocument": {
                                "Statement": [
                                    {
                                        "Resource": {"Fn::GetAtt": ["MyBucket", "Arn"]},
                                    }
                                ]
                            }
                        },
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) == 1
        assert dep_edges[0].source == "cfn:infra/template.yaml:resource:MyPolicy"
        assert dep_edges[0].target == "cfn:infra/template.yaml:resource:MyBucket"

    def test_detects_multiple_refs_in_one_resource(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "Resources": {
                    "MyVPC": {"Type": "AWS::EC2::VPC", "Properties": {}},
                    "MySecurityGroup": {"Type": "AWS::EC2::SecurityGroup", "Properties": {}},
                    "MyInstance": {
                        "Type": "AWS::EC2::Instance",
                        "Properties": {
                            "SubnetId": {"Ref": "MyVPC"},
                            "SecurityGroupIds": [{"Ref": "MySecurityGroup"}],
                        },
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        instance_deps = [
            e for e in result.edges
            if e.kind == EdgeKind.DEPENDS_ON
            and e.source == "cfn:infra/template.yaml:resource:MyInstance"
        ]
        assert len(instance_deps) == 2
        targets = {e.target for e in instance_deps}
        assert "cfn:infra/template.yaml:resource:MyVPC" in targets
        assert "cfn:infra/template.yaml:resource:MySecurityGroup" in targets

    def test_no_self_reference(self):
        """A resource that Refs itself should not create a self-dependency edge."""
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "MyBucket": {
                        "Type": "AWS::S3::Bucket",
                        "Properties": {
                            "Tags": [{"Value": {"Ref": "MyBucket"}}],
                        },
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) == 0

    # --- Positive: Parameters detection ---

    def test_detects_parameters(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Parameters": {
                    "EnvironmentName": {
                        "Type": "String",
                        "Default": "production",
                        "Description": "The environment name",
                    },
                    "InstanceType": {
                        "Type": "String",
                        "Default": "t3.micro",
                    },
                },
                "Resources": {},
            },
        })
        result = self.detector.detect(ctx)
        params = [n for n in result.nodes if n.kind == NodeKind.CONFIG_DEFINITION and n.properties.get("cfn_type") == "parameter"]
        assert len(params) == 2
        param_names = {n.label for n in params}
        assert "param:EnvironmentName" in param_names
        assert "param:InstanceType" in param_names
        env_param = next(n for n in params if "EnvironmentName" in n.label)
        assert env_param.properties["default"] == "production"
        assert env_param.properties["description"] == "The environment name"

    # --- Positive: Outputs detection ---

    def test_detects_outputs(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "MyBucket": {"Type": "AWS::S3::Bucket"},
                },
                "Outputs": {
                    "BucketArn": {
                        "Description": "ARN of the S3 bucket",
                        "Value": {"Fn::GetAtt": ["MyBucket", "Arn"]},
                        "Export": {"Name": "my-bucket-arn"},
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        outputs = [n for n in result.nodes if n.kind == NodeKind.CONFIG_DEFINITION and n.properties.get("cfn_type") == "output"]
        assert len(outputs) == 1
        assert outputs[0].label == "output:BucketArn"
        assert outputs[0].properties["description"] == "ARN of the S3 bucket"
        assert outputs[0].properties["export_name"] == "my-bucket-arn"

    # --- Positive: JSON format ---

    def test_detects_json_format(self):
        ctx = _ctx(
            parsed_data={
                "type": "json",
                "data": {
                    "AWSTemplateFormatVersion": "2010-09-09",
                    "Resources": {
                        "MyFunction": {
                            "Type": "AWS::Lambda::Function",
                            "Properties": {"Runtime": "python3.9"},
                        },
                    },
                },
            },
            file_path="infra/template.json",
            language="json",
        )
        result = self.detector.detect(ctx)
        resources = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE]
        assert len(resources) == 1
        assert resources[0].properties["resource_type"] == "AWS::Lambda::Function"

    # --- Positive: Detection without AWSTemplateFormatVersion ---

    def test_detects_without_version_key(self):
        """Resources with AWS:: types should be detected even without AWSTemplateFormatVersion."""
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "Resources": {
                    "MyTopic": {
                        "Type": "AWS::SNS::Topic",
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        resources = [n for n in result.nodes if n.kind == NodeKind.INFRA_RESOURCE]
        assert len(resources) == 1

    # --- Negative tests ---

    def test_empty_parsed_data(self):
        ctx = _ctx(None)
        result = self.detector.detect(ctx)
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_non_cfn_yaml(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "Deployment",
                "apiVersion": "apps/v1",
                "metadata": {"name": "myapp"},
            },
        })
        result = self.detector.detect(ctx)
        assert len(result.nodes) == 0

    def test_non_aws_resources(self):
        """Resources without AWS:: prefix should not trigger detection."""
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "Resources": {
                    "MyResource": {
                        "Type": "Custom::MyResource",
                    },
                },
            },
        })
        result = self.detector.detect(ctx)
        assert len(result.nodes) == 0

    def test_wrong_parsed_type(self):
        ctx = _ctx({
            "type": "xml",
            "data": {"something": "else"},
        })
        result = self.detector.detect(ctx)
        assert len(result.nodes) == 0

    # --- Determinism tests ---

    def test_determinism_resources(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "BucketA": {"Type": "AWS::S3::Bucket"},
                    "BucketB": {"Type": "AWS::S3::Bucket"},
                    "QueueC": {"Type": "AWS::SQS::Queue"},
                },
            },
        })
        r1 = self.detector.detect(ctx)
        r2 = self.detector.detect(ctx)
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)

    def test_determinism_with_deps(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "AWSTemplateFormatVersion": "2010-09-09",
                "Resources": {
                    "VPC": {"Type": "AWS::EC2::VPC", "Properties": {"CidrBlock": "10.0.0.0/16"}},
                    "Subnet": {
                        "Type": "AWS::EC2::Subnet",
                        "Properties": {"VpcId": {"Ref": "VPC"}},
                    },
                },
                "Parameters": {
                    "Env": {"Type": "String"},
                },
                "Outputs": {
                    "VpcId": {"Value": {"Ref": "VPC"}},
                },
            },
        })
        r1 = self.detector.detect(ctx)
        r2 = self.detector.detect(ctx)
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [(e.source, e.target) for e in r1.edges] == [(e.source, e.target) for e in r2.edges]

    def test_returns_detector_result(self):
        result = self.detector.detect(_ctx(None))
        assert isinstance(result, DetectorResult)
