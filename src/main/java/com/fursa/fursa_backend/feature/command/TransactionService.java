package com.fursa.fursa_backend.feature.command;

import java.util.Stack;

public class TransactionService {

    private final Stack<Command> commandHistory = new Stack<>();

    public void executeCommand(Command command) {
        command.execute();
        commandHistory.push(command);
    }

    public void undoLastCommand() {
        if(!commandHistory.isEmpty()) {
            Command command = commandHistory.pop();
            command.undo();
        }
    }

}
