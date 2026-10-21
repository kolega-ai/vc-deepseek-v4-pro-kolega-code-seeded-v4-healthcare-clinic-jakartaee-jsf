pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
    }

    environment {
        MAVEN_OPTS = '-Xmx1024m'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Unit tests') {
            steps {
                sh './mvnw -B -ntp -e clean verify'
            }
        }

        stage('Package') {
            steps {
                sh 'ls -la target/clinic-portal.war'
                archiveArtifacts artifacts: 'target/clinic-portal.war', fingerprint: true
            }
        }

        stage('Report') {
            steps {
                junit 'target/surefire-reports/*.xml'
                recordIssues enabledForFailure: true, tools: [java(), mavenConsole()]
            }
        }
    }

    post {
        failure {
            echo 'Build failed - see test and Maven reports above.'
        }
        success {
            echo 'Clinic portal WAR built successfully.'
        }
    }
}
