pipeline {
    agent {
        docker {
           image 'maven:3-jdk-11'
            args '-v /home/docker/jenkins/files/m2:/root/.m2'
        }
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -DskipTests clean package'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar, target/*.exe'
                }
            }
        }
        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
    }
}