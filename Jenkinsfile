pipeline {
    agent {
        docker {
           image 'maven:3-jdk-11'
            args '-v /home/docker/jenkins/files/m2:/root/.m2'
        }
    }

    stages {
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }
    }
    post {
            always {
                archiveArtifacts artifacts: 'target/*.jar, target/*.exe', onlyIfSuccessful: true
            }
    }
}