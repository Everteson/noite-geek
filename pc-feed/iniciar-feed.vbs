' Inicia o feed da TV (Noite Geek) escondido, sem janela.
' Pra iniciar junto com o Windows: Win+R, digite  shell:startup  e crie la um ATALHO pra este arquivo
' (botao direito > Criar atalho). Pra parar de iniciar sozinho: apague o atalho de la.
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
q = Chr(34)
CreateObject("WScript.Shell").Run q & dir & "\venv\Scripts\pythonw.exe" & q & " -I " & q & dir & "\feed_server.py" & q, 0, False
